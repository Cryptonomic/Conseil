package tech.cryptonomic.conseil.tezos.michelson.contracts

import scala.concurrent.SyncVar
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.config.Platforms.TNSContractConfiguration
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  AccountId,
  Contract,
  ContractId,
  Decimal,
  Micheline,
  Parameters,
  ScriptId,
  Transaction
}
import tech.cryptonomic.conseil.tezos.TezosTypes.InternalOperationResults.{Transaction => InternalTransaction}
import tech.cryptonomic.conseil.tezos.Tables.BigMapsRow
import tech.cryptonomic.conseil.util.OptionUtil.whenOpt
import com.typesafe.scalalogging.Logger

/** Extract information related to a specific contract shape.
  *
  * We don't expect more than 1 contract being available at a time, but
  * we need to consider possible upgrades to the same contract, in time.
  */
trait TNSContract {
  import TNSContract._

  /** Does the Id reference a known TNS smart contract? */
  def isKnownRegistrar(registrar: ContractId): Boolean

  /** Call this to store big-map-ids associated with a TNS contract.
    * This is supposed to happen once the chain records a block originating
    * the contract identified via [[isKnownRegistrar]].
    *
    * Matter-of-factly the actual rows storing the map data
    * for such contract will be expected as parameters.
    *
    * @param registrar the contract identifier
    * @param maps all big maps stored for the registrar
    */
  def setMaps(registrar: ContractId, maps: List[BigMapsRow]): Unit

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
  def readLookupMapReference(transaction: Either[Transaction, InternalTransaction]): Option[LookupMapReference]

  /** Extracts any available data for a TNS entry for the specific contract
    *
    * @param registrar the id of the smart contract
    * @param content a json string representing the micheline content of a specific lookup map
    * @return the name record if both the contract was found and the content was well-formed
    */
  def readLookupMapContent(registrar: ContractId, content: String): Option[NameRecord]
}

object TNSContract extends LazyLogging {

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

  /** Creates the instance based on the configuration values.
    *
    * @param config a configuration entry
    * @return the new contract
    */
  def fromConfig(config: TNSContractConfiguration): TNSContract = {
    logger.info("Creating the TNS contract object from the following values: {}", config)
    new ConfiguredContract(ContractId(config.accountId))
  }

  /** This is a no-op contract, it never matches any address, nor does it returns any useful information.
    * Use this value as a fallback when there's not enough information
    * to define a real one
    */
  val noContract: TNSContract = new TNSContract {

    def isKnownRegistrar(registrar: ContractId): Boolean = false

    def setMaps(registrar: ContractId, maps: List[BigMapsRow]): Unit = ()

    def readLookupMapReference(transaction: Either[Transaction, InternalTransaction]): Option[LookupMapReference] = None

    def readLookupMapContent(registrar: ContractId, content: String): Option[NameRecord] = None

  }

  /** Fully operational contract referring to a specific tezos network contract address
    *
    * @param registrar the contract address
    * @param lookupMapId where the lookup values are stored, using the registered name as key
    * @param reverseMapId the reverse lookup, where the key is the resolved address
    */
  class ConfiguredContract(
      private val id: ContractId,
      private val lookupMapId: SyncVar[TNSContract.BigMapId] = new SyncVar(),
      private val reverseMapId: SyncVar[TNSContract.BigMapId] = new SyncVar()
  ) extends TNSContract {

    def isKnownRegistrar(registrar: ContractId): Boolean = registrar == id

    /* Will interpret data on the tns call from the transaction's parameters
     *
     * @param params transaction call parameters, containing michelson code
     * @return extracted tns record name and the corresponding resolved address, if the input is valid
     */
    private def readMapping(params: Parameters): Option[(Name, AccountId)] =
      for {
        ep <- params.entrypoint if ep.trim == "registerName"
        nameMapping <- MichelineOps.parseNameRegistrationFromParameters(params.value)
      } yield nameMapping

    override def readLookupMapReference(
        transaction: Either[Transaction, InternalTransaction]
    ): Option[LookupMapReference] = {
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
      whenOpt(isKnownRegistrar(destination)) {
        for {
          updateMaps <- mapDiff.map(collectUpdateMaps)
          if allRegisteredIdsUpdated(Set(lookupMapId, reverseMapId), updateMaps.keySet, destination)
          operationParams <- parameters
          params <- operationParams.swap.toOption
          (name, resolver) <- readMapping(params)
          id <- lookupMapId.get(timeout = 0L)
          (key, keyHash) <- updateMaps.get(id)
        } yield LookupMapReference(destination, name, resolver, id, keyHash)
      }
    }

    override def readLookupMapContent(registrar: ContractId, content: String): Option[NameRecord] =
      whenOpt(isKnownRegistrar(registrar)) { MichelineOps.parseReverseLookupContent(content) }

    /** Call this to store big-map-ids associated with a TNS contract.
      * This is supposed to happen once the chain records a block originating
      * the contract identified via [[isKnownRegistrar]].
      * This will be needed to identify the right map tracking TNS operation
      * updates.
      *
      * @param registrar the contract identifier
      * @param lookupId the id of the map used to store names mapping
      * @param reverseId the id of the map used to store reverse names mapping
      */
    def setMapIds(registrar: ContractId, lookupId: BigDecimal, reverseId: BigDecimal): Unit =
      if (id == registrar) {
        lookupMapId.put(BigMapId(lookupId))
        reverseMapId.put(BigMapId(reverseId))
        Logger[TNSContract.type].info(
          "Registered big map references for the TNS contract {}. Lookup id {}, reverse lookup id {}.",
          registrar.id,
          lookupId,
          reverseId
        )
      }

    override def setMaps(registrar: ContractId, maps: List[BigMapsRow]): Unit = {
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
    val check = valuesAvailable && registeredIds.forall(id => updateIds.contains(id.get))
    logger.whenDebugEnabled(
      logger.debug(
        "Checking updated map ids {}, upon a call to the TNS Contract {}. Ids matching? {}",
        updateIds.mkString("{", ",", "}"),
        registrar.id,
        check
      )
    )
    check
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

    /* This is the expression matcher we expect for the valid call parameters
     * in michelson format.
     * We're interested in the inner Pair, containing name and resolver.
     */
    private val MichelsonParamExpr = """^Pair \d+ \(Pair\s+"(.+)"\s+"(.+)"\)$""".r

    /* This is the equivalent matcher for micheline format, in case the michelson
     * conversion failed for any reason, and paramters are still in "raw format"
     */
    private def parseAsMicheline(micheline: String) =
      JsonParser.parse[MichelsonInstruction](micheline).toOption.collect {
        case MichelsonSingleInstruction(
            "Pair",
            _ :: MichelsonType("Pair", MichelsonStringConstant(name) :: MichelsonStringConstant(resolver) :: _, _) :: _,
            _
            ) =>
          (Name(name), AccountId(resolver))
      }

    /* Reads paramters for a registerName call, extracting the name mapping */
    def parseNameRegistrationFromParameters(paramCode: Micheline): Option[(Name, AccountId)] = {

      logger.info("Parsing parameters for TNS maps identification")

      //actually the parameters are already parsed and rendered as concrete michelson syntax
      val michelson = paramCode.expression.trim

      whenOpt(michelson.nonEmpty) {
        //we profit from scala natively-provided pattern matching on regex to extract named groups
        val extracted = MichelsonParamExpr
          .findFirstIn(michelson)
          .map {
            case MichelsonParamExpr(name, resolver) => (Name(name), AccountId(resolver))
          }
          .orElse(parseAsMicheline(michelson))
        if (extracted.isEmpty)
          logger.warn(
            "The TNS call parameters didn't conform to the expected shape for the contract. Michelson code was {}",
            paramCode.expression
          )
        extracted
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

      /* This is the currently expected parse result for a valid tns reverse-lookup map content
       * The parser seems not to handle list recursion of types with the same details
       * therefore we have generic MichelsonType entries, which doesn't really fit
       */
      val extracted = parsed.toOption.collect {
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

      if (extracted.isEmpty)
        logger.warn(
          "The TNS map reverse-lookup content didn't conform to the expected shape for the contract. Micheline was {}, which parses to {}",
          micheline,
          parsed
        )
      extracted

    }
  }
}
