package tech.cryptonomic.conseil.tezos.michelson.contracts

import scala.collection.immutable.TreeSet
import scala.concurrent.SyncVar
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes.ContractId

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
      case ContractToolbox(_, lookupVar, reversVar) =>
        lookupVar.put(BigMapId(lookupId))
        reversVar.put(BigMapId(reverseId))
    }

}

object TNSContracts extends LazyLogging {

  /** typed wrapper to clarify the meaning of the numerical id */
  case class BigMapId(id: BigDecimal) extends AnyVal

  private case class ContractToolbox(
      id: ContractId,
      lookupMapId: SyncVar[BigMapId] = new SyncVar(),
      reverseMapId: SyncVar[BigMapId] = new SyncVar()
  )

  //we sort toolboxes by the contract id
  implicit private val toolboxOrdering: Ordering[ContractToolbox] = Ordering.by(_.id.id)

  /* Creates a new toolbox, only if the type is a known one, or returns an empty Option */
  private def newToolbox(id: ContractId, contractType: String) =
    PartialFunction.condOpt(contractType) {
      //we're not actually selecting the logic, we assume a convention here, and that any id passed is valid.
      case _ => ContractToolbox(id)
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

    logger.info("The following naming service contracts were actually registered: {}", toolboxes.map(_.id.id).mkString(","))
    // we keep the token tools in a sorted set to speed up searching
    new TNSContracts(TreeSet(toolboxes: _*))
  }

}
