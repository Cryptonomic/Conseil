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
import tech.cryptonomic.conseil.tezos.TezosTypes.Contract.BigMapAlloc

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
    * @return a name resolver, if any was correctly registered by the operation
    */
  def readLookupMapping(transaction: Either[Transaction, InternalTransaction]): Option[(Name, AccountId)] = {
    //the data shape is roughly the same, so we can extract common pieces easily
    val (parameters, destination, mapDiff) =
      transaction
        .bimap(
          t => (t.parameters, t.destination, t.metadata.operation_result.big_map_diff),
          t => (t.parameters, t.destination, t.result.big_map_diff)
        )
        .merge

    for {
      ContractToolbox(_, lookupId, reverseId, readMapping) <- registry.find(_.id == destination)
      updatedMaps <- mapDiff.map(collectUpdatedMapIds)
      if mapIdsMatchAny(lookupId, updatedMaps, destination) && mapIdsMatchAny(reverseId, updatedMaps, destination)
      operationParams <- parameters
      params <- operationParams.swap.toOption
      (name, resolver) <- readMapping(params)
    } yield name -> resolver
  }

  /** Call this to store a big-map-ids associated with a TNS contract.
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
      case ContractToolbox(_, lookupVar, reverseVar, _) =>
        lookupVar.put(BigMapId(lookupId))
        reverseVar.put(BigMapId(reverseId))
    }

    def setMaps(registrar: ContractId, maps: List[BigMapAlloc]): Unit = ???

}

object TNSContracts extends LazyLogging {

  /** typed wrapper to clarify the meaning of the numerical id */
  case class BigMapId(id: BigDecimal) extends AnyVal

  /** typed wrapper to clarify the meaning of the name string */
  case class Name(value: String) extends AnyVal

  /** Extracts relevant data to query the node about all details regarding the naming entry
    *  The tuple values collects: registered name, corresponding account address
    */
  type LookupMappingReader = Parameters => Option[(Name, AccountId)]

  private case class ContractToolbox(
      id: ContractId,
      lookupMapId: SyncVar[BigMapId] = new SyncVar(),
      reverseMapId: SyncVar[BigMapId] = new SyncVar(),
      lookupReader: LookupMappingReader
  )

  //we sort toolboxes by the contract id
  implicit private val toolboxOrdering: Ordering[ContractToolbox] = Ordering.by(_.id.id)

  /* Creates a new toolbox, only if the type is a known one, or returns an empty Option */
  private def newToolbox(id: ContractId, contractType: String) =
    PartialFunction.condOpt(contractType) {
      //we're not actually selecting the logic, we assume a convention here, and that any id passed is valid.
      case _ =>
        ContractToolbox(
          id,
          lookupReader = params =>
            for {
              ep <- params.entrypoint if ep == "registerName"
              nameMapping <- MichelineOps.parseNameRegistrationFromParameters(params.value)
            } yield nameMapping
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

  /** Will check if the id registered, possibly not yet set, matches with any of the values referred from the update */
  private def mapIdsMatchAny(
      registeredId: SyncVar[BigMapId],
      updateIds: List[BigMapId],
      registrar: ContractId
  ): Boolean = {
    if (!registeredId.isSet)
      logger.error(
        """A name registration was found where one of the maps for [reverse] lookup is not yet identified from contract origination
          | map_ids: {}
          | registrar: {}""".stripMargin,
        updateIds.mkString(","),
        registrar
      )
    registeredId.isSet && updateIds.contains(registeredId.get.id)
  }

  /** Will extract all updated map ids from a list of diffs */
  private def collectUpdatedMapIds(diffs: List[Contract.CompatBigMapDiff]): List[BigMapId] =
    diffs.collect {
      case Left(Contract.BigMapUpdate("update", _, _, Decimal(mapId), _)) => BigMapId(mapId)
    }

  /** Defines extraction operations based on micheline fields */
  private object MichelineOps {

    /*TODO add docs... and implementation!*/
    def parseNameRegistrationFromParameters(paramCode: Micheline): Option[(Name, AccountId)] = ???

  }
}
