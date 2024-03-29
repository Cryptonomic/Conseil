package tech.cryptonomic.conseil.indexer.tezos

import cats.Show
import cats.implicits._
import slick.dbio.DBIO
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.tezos.TezosOptics.Operations.extractAppliedTransactions
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{Block, ContractId, PublicKeyHash, ScriptId}
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TNSContract
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TNSContract.{
  BigMapId,
  LookupMapReference,
  Name,
  NameRecord
}
import tech.cryptonomic.conseil.common.util.JsonUtil.JsonString

import scala.concurrent.{ExecutionContext, Future}

/** High-level processing of TNS data
  *
  * @param tnsContracts custom definitions for the tns
  * @param node an operator to access data on chain node
  */
private[tezos] class TezosNamesOperations(tnsContracts: TNSContract, node: TezosNodeOperator)
    extends ConseilLogSupport {
  implicit val showLookupReferences: Show[LookupMapReference] = Show.show {
    case LookupMapReference(
          ContractId(contractId),
          Name(name),
          PublicKeyHash(accountId),
          BigMapId(bigMapId),
          ScriptId(keyHash)
        ) =>
      s"""Contract: $contractId, registeredName: $name, address: $accountId, big map: $bigMapId, key: $keyHash"""

  }

  /** Handles recording of name-address lookups registered via TNS smart contracts */
  def processNamesRegistrations(blocks: List[Block])(implicit ec: ExecutionContext): Future[DBIO[Option[Int]]] = {

    val jsonResponse = (extractNameMapsReferences andThen fetchContent)(blocks)

    jsonResponse.map(parseContent andThen TezosDatabaseOperations.upsertTezosNames)

  }

  /* Reads the json into name records, according to the tns contract logic */
  private val parseContent: List[(LookupMapReference, JsonString)] => List[NameRecord] =
    mapContents =>
      mapContents.collect {
        case (lookupRef, JsonString(json)) if json.trim.nonEmpty =>
          tnsContracts
            .readLookupMapContent(lookupRef.contractId, json)
            .filter(nameRecord =>
              //double-check we're looking at the right data for the call
              nameRecord.name == lookupRef.lookupName.value && nameRecord.resolver == lookupRef.resolver.value
            )
      }.flattenOption

  /* call the remote node to get map contents, as referred by the input values */
  private def fetchContent(implicit
      ec: ExecutionContext
  ): Map[Block, List[LookupMapReference]] => Future[List[(LookupMapReference, JsonString)]] =
    mapReferences =>
      mapReferences.toList.traverse { case (block, references) =>
        val showReferences = references.map(_.show).mkString("\n")
        if (references.nonEmpty)
          logger.info(
            s"About to fetch big map contents to find TNS name registrations for block ${block.data.hash.value} at level ${block.data.header.level} and the following references:\n $showReferences"
          )
        references.traverse { ref =>
          node.getBigMapContents(block.data.hash, ref.mapId.id, ref.mapKeyHash).map(ref -> _)
        }

      }.map(_.flatten)

  /* gets structured data that reference big maps from the right tns transactions
   * the maps will contain the resulting name records
   */
  private val extractNameMapsReferences: List[Block] => Map[Block, List[LookupMapReference]] =
    blocks =>
      blocks.map { b =>
        val refs = extractAppliedTransactions(b).filter {
          case Left(op) => tnsContracts.isKnownRegistrar(op.destination)
          case Right(op) => tnsContracts.isKnownRegistrar(op.destination)
        }.map(tnsContracts.readLookupMapReference).flattenOption

        b -> refs

      }.toMap

}
