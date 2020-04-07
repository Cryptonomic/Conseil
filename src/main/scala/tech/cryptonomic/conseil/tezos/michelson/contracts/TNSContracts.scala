package tech.cryptonomic.conseil.tezos.michelson.contracts

import scala.collection.immutable.TreeSet
import scala.concurrent.SyncVar
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  AccountId,
  Contract,
  ContractId,
  Decimal,
  Micheline,
  Parameters,
  Transaction
}
import tech.cryptonomic.conseil.tezos.TezosTypes.InternalOperationResults.{Transaction => InternalTransaction}
import tech.cryptonomic.conseil.tezos.Tables.BigMapsRow
import tech.cryptonomic.conseil.tezos.TezosTypes.ScriptId

/** For each specific contract available we store a few
  * relevant bits of data useful to extract information
  * related to that specific contract shape.
  *
  * We don't expect more than 1 contract being available at a time, but
  * we need to consider possible upgrades to the same contract, in time.
  */
class TNSContracts(private val registry: Set[TNSContracts.ContractToolbox]) {
  import TNSContracts._

  /** Does the Id reference a known TNS smart contract? */
  def isKnownRegistrar(registrar: ContractId): Boolean = registry.exists(_.id == registrar)

  /** Extracts a naming entry, with the corresponding map key (hashed) to retrieve
    * full info for the name registry item.
    *
    * The call will read michelson data in the transaction to extract it.
    * To reduce any invalid interpretation of data, additional checks will be
    * made to verify that the TNS contract referred is a known one, with the
    * expected big maps being used.
    *
    * @param transaction accepts either a transaction or internal transaction
    * @return a name resolver data, if any was correctly registered by the operation
    */
  def readLookupMapReference(transaction: Either[Transaction, InternalTransaction]): Option[LookupMapReference] = {
    //the data shape is roughly the same, so we can extract common pieces easily
    val (parameters, destination, mapDiff) =
      transaction
        .bimap(
          t => (t.parameters, t.destination, t.metadata.operation_result.big_map_diff),
          t => (t.parameters, t.destination, t.result.big_map_diff)
        )
        .merge

    /* this is a rough outline
     * we take only meaningful maps and check the ids, against the lookup maps for the contract
     * then we extract data both from parameters and big map diffs to create a
     * return object with all needed references to eventually
     * read all the tns info from the tezos node.
     */
    for {
      ContractToolbox(_, lookupId, reverseId, readMapping, _) <- registry.find(_.id == destination)
      updateMaps <- mapDiff.map(collectUpdateMaps)
      if allRegisteredIdsUpdated(Set(lookupId, reverseId), updateMaps.keySet, destination)
      operationParams <- parameters
      params <- operationParams.swap.toOption
      (name, resolver) <- readMapping(params)
      id <- reverseId.get(timeout = 0L)
      (key, keyHash) <- updateMaps.get(id)
    } yield LookupMapReference(destination, name, resolver, id, keyHash)
  }

  /** Extracts any available data for a TNS entry for the specific contract
    *
    * @param registrar the id of the smart contract
    * @param content a json string representing the micheline content of a specific lookup map
    * @return the name record if both the contract was found and the content was well-formed
    */
  def readLookupMapContent(registrar: ContractId, content: String): Option[NameRecord] =
    for {
      ContractToolbox(_, _, _, _, readContent) <- registry.find(_.id == registrar)
      record <- readContent(content)
    } yield record

  /** Call this to store big-map-ids associated with a TNS contract.
    * This is supposed to happen once the chain records a block originating
    * one of the contracts identified via [[isKnownTNS]].
    * This will be needed to identify the right map tracking TNS operation
    * updates
    *
    * @param registrar the contract identifier
    * @param lookupId the id of the map used to store names mapping
    * @param reverseId the id of the map used to store reverse names mapping
    */
  def setMapIds(registrar: ContractId, lookupId: BigDecimal, reverseId: BigDecimal): Unit =
    registry.find(_.id == registrar).foreach {
      case ContractToolbox(_, lookupVar, reverseVar, _, _) =>
        lookupVar.put(BigMapId(lookupId))
        reverseVar.put(BigMapId(reverseId))
    }

  /** Call this to store big-map-ids associated with a TNS contract.
    * This is supposed to happen once the chain records a block originating
    * one of the contracts identified via [[isKnownTNS]].
    * Matter-of-factly the actual rows storing the map data
    * for such contract will be expected as parameters.
    *
    * @param registrar the contract identifier
    * @param maps all big maps stored for the registrar
    */
  def setMaps(registrar: ContractId, maps: List[BigMapsRow]): Unit = {
    /* Currently we only match with a specific contract definition,
     * therefore we know exactly how to identify the role of each map, by the key and value type
     *
     * We expect the reverse map to be a simple address to name mapping.
     * Any other map (there should be exactly two) must then be the lookup, by exclusion.
     */
    val (reverseMaps, lookupMaps) = maps.partition {
      case BigMapsRow(id, keyType, valueType) =>
        (keyType, valueType) match {
          case (Some("address"), Some("string")) => true
          case _ => false
        }
    }

    (reverseMaps.headOption.map(_.bigMapId), lookupMaps.headOption.map(_.bigMapId)).tupled.foreach {
      case (reverseId, lookupId) => setMapIds(registrar, lookupId, reverseId)
    }

  }

}

object TNSContracts extends LazyLogging {

  /** typed wrapper to clarify the meaning of the numerical id */
  case class BigMapId(id: BigDecimal) extends AnyVal

  /** typed wrapper to clarify the meaning of the name string */
  case class Name(value: String) extends AnyVal

  /** Wraps the data extracted to identify lookup information on a stored big map
    * @param contractId the smart contract reference
    * @param lookupName name to lookup for
    * @param resolver the resolved account address
    * @param mapId the map holding the tns registration info
    * @param mapKeyHash the hashed key for the tns info in the big map
    */
  case class LookupMapReference(
      contractId: ContractId,
      lookupName: Name,
      resolver: AccountId,
      mapId: BigMapId,
      mapKeyHash: ScriptId
  )

  /** Contains all data available from a tns entry
    *
    * @param name the registered name
    * @param updated a boolean flag indicating if the entry was modified
    * @param resolver the account address referred to by the name
    * @param registeredAt ISO timestamp of the registration moment
    * @param registrationPeriod chain period of the registration
    * @param owner an account address corresponding to the name record owner
    */
  case class NameRecord(
      name: String,
      updated: String,
      resolver: String,
      registeredAt: String,
      registrationPeriod: String,
      owner: String
  )

  /** Extracts relevant data to query the node about all details regarding the naming entry
    *  The tuple values collects: registered name, corresponding account address
    */
  type LookupMappingReader = Parameters => Option[(Name, AccountId)]

  /** Extracts a TNS entry from a json micheline description, according to the contract */
  type NameRecordReader = String => Option[NameRecord]

  private case class ContractToolbox(
      id: ContractId,
      lookupMapId: SyncVar[BigMapId] = new SyncVar(),
      reverseMapId: SyncVar[BigMapId] = new SyncVar(),
      lookupReader: LookupMappingReader,
      recordReader: NameRecordReader
  )

  //we sort toolboxes by the contract id
  implicit private val toolboxOrdering: Ordering[ContractToolbox] = Ordering.by(_.id.id)

  /* Creates a new toolbox, only if the type is a known one, or returns an empty Option */
  private def newToolbox(id: ContractId, contractType: String) =
    PartialFunction.condOpt(contractType) {
      //we're not actually selecting the logic, we assume a convention here, and that any id passed is valid.
      //it's plausible that we might want to change this to only accept a named type for cryptonomic in-house contract type.
      case _ =>
        ContractToolbox(
          id,
          lookupReader = params =>
            for {
              ep <- params.entrypoint if ep == "registerName"
              nameMapping <- MichelineOps.parseNameRegistrationFromParameters(params.value)
            } yield nameMapping,
          recordReader = MichelineOps.parseReverseLookupContent
        )
    }

  /** Builds a registry of TNS contracts with the data passed-in
    *
    * @param knownRegistrars the pair of id and type of contract used, the latter as a String
    */
  def fromConfig(knownRegistrars: List[(ContractId, String)]): TNSContracts = {
    logger.info("Creating a TNS registry from the following values: {}", knownRegistrars.map {
      case (cid, contractType) => cid.id -> contractType
    }.mkString(","))

    val toolboxes = knownRegistrars.flatMap {
      case (cid, contractType) => newToolbox(cid, contractType)
    }

    logger.info(
      "The following naming service contracts were actually registered: {}",
      toolboxes.map(_.id.id).mkString(",")
    )
    // we keep the token tools in a sorted set to speed up searching
    new TNSContracts(TreeSet(toolboxes: _*))
  }

  /** Will check if all registered ids, possibly not yet set, match with any of the values referred from the update */
  private def allRegisteredIdsUpdated(
      registeredIds: Set[SyncVar[BigMapId]],
      updateIds: Set[BigMapId],
      registrar: ContractId
  ): Boolean = {
    val valuesAvailable = registeredIds.forall(_.isSet)
    if (!valuesAvailable)
      logger.error(
        """A name registration was found where one of the maps for [reverse] lookup is not yet identified from contract origination
          | map_ids: {}
          | registrar: {}""".stripMargin,
        updateIds.mkString(","),
        registrar
      )
    valuesAvailable && registeredIds.forall(id => updateIds.contains(id.get))
  }

  /** Will extract all updated map ids from a list of diffs */
  private def collectUpdateMaps(diffs: List[Contract.CompatBigMapDiff]): Map[BigMapId, (Micheline, ScriptId)] =
    diffs.collect {
      case Left(Contract.BigMapUpdate("update", key, keyHash, Decimal(mapId), _)) => BigMapId(mapId) -> (key, keyHash)
    }.toMap

  /** Defines extraction operations based on micheline fields */
  private object MichelineOps {
    import tech.cryptonomic.conseil.tezos.michelson.dto._
    import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser

    /* Reads paramters for a registerName call, extracting the name mapping */
    def parseNameRegistrationFromParameters(paramCode: Micheline): Option[(Name, AccountId)] = {

      val parsed = JsonParser.parse[MichelsonInstruction](paramCode.expression)

      parsed.left.foreach(
        err =>
          logger.error(
            """Failed to parse michelson expression for TNS registration call.
              | Parameters were: {}
              | Error is {}""".stripMargin,
            paramCode.expression,
            err.getMessage()
          )
      )

      parsed.toOption.collect {
        case MichelsonSingleInstruction(
            "Pair",
            MichelsonIntConstant(duration) :: MichelsonSingleInstruction(
                  "Pair",
                  MichelsonStringConstant(name) :: MichelsonStringConstant(resover) :: _,
                  _
                ) :: _,
            _
            ) =>
          (Name(name), AccountId(resover))
      }
    }

    /** Given a correctly formatted json string parse
      * it as a reverse lookup big map entry, according to the
      * specific structure of cryptnomic tns smart contract
      *
      * @param micheline a valid json string, representing the map content
      * @return the values extracted from the micheline structure
      */
    def parseReverseLookupContent(micheline: String): Option[NameRecord] = {

      val parsed = JsonParser.parse[MichelsonInstruction](micheline)

      parsed.left.foreach(
        err =>
          logger.error(
            """Failed to parse michelson expression for TNS map entry.
              | Content was: {}
              | Error is {}""".stripMargin,
            micheline,
            err.getMessage()
          )
      )

      parsed.toOption.collect {

        // format: off
        case MichelsonSingleInstruction("Pair",
            MichelsonType("Pair",
                MichelsonType(updated, _, _) ::
                MichelsonType("Pair",
                    MichelsonStringConstant(name) :: MichelsonStringConstant(owner) :: _,
                _) ::
                _,
            _) ::
            MichelsonType("Pair",
                MichelsonStringConstant(registeredAt) ::
                MichelsonType("Pair",
                    MichelsonIntConstant(registrationPeriod) :: MichelsonStringConstant(resolver) :: _,
                _) ::
                _ ,
            _) ::
            _,
        _) => NameRecord(name, updated, resolver, registeredAt, registrationPeriod, owner)
        // format: on

      }

    }
  }
}
