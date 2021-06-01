package tech.cryptonomic.conseil.indexer.tezos.processing

import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import cats.implicits._
import tech.cryptonomic.conseil.indexer.tezos.{TezosGovernanceOperations, TezosNamesOperations, TezosNodeOperator, Tzip16MetadataOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.tezos.TezosTypes
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{Block, InternalOperationResults, Voting}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Syntax._
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TNSContract
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts.Tzip16
import tech.cryptonomic.conseil.indexer.tezos.michelson.renderer.MichelsonRenderer

/** Collects operations related to handling blocks from
  * the tezos node.
  *
  * @param nodeOperator connects to tezos
  * @param db raw access to the slick database
  * @param tnsOperations module providing tns processing operations
  * @param accountsProcessor module providing entity-related operations
  * @param bakersProcessor module providing entity-related operations
  * @param tokens configured definitions of token contracts for the network
  * @param tns configured definitions of tns contract for the network
  */
class BlocksProcessor(
    nodeOperator: TezosNodeOperator,
    db: Database,
    tnsOperations: TezosNamesOperations,
    accountsProcessor: AccountsProcessor,
    bakersProcessor: BakersProcessor,
    metadataOperator: Tzip16MetadataOperator
)(implicit tokens: TokenContracts, tns: TNSContract)
    extends ConseilLogSupport {

  /* will store a single page of block results */
  private[tezos] def processBlocksPage(results: nodeOperator.BlockFetchingResults)(
      implicit ec: ExecutionContext
  ): Future[Int] = {
    def logBlockOutcome[A]: PartialFunction[Try[Option[A]], Unit] = {
      case Success(accountsCount) =>
        val showCount = accountsCount.fold("")(" " + _)
        logger.info(s"Wrote ${results.size} blocks to the database, checkpoint stored for$showCount account updates")
      case Failure(e) =>
        logger.error("Could not write blocks or accounts checkpoints to the database.", e)
    }

    //ignore the account ids for storage, and prepare the checkpoint account data
    //we do this on a single sweep over the list, pairing the results and then unzipping the outcome
    val (blocks, accountUpdates) =
      results.map {
        case (block, accountIds) =>
          block -> accountIds.taggedWithBlockData(block.data)
      }.unzip

    for {
      _ <- db.run(TezosDb.writeBlocksAndCheckpointAccounts(blocks, accountUpdates)) andThen logBlockOutcome
      _ <- processTzip16Metadata(blocks)
      _ <- tnsOperations.processNamesRegistrations(blocks).flatMap(db.run)
      bakersCheckpoints <- accountsProcessor.processAccountsForBlocks(accountUpdates) // should this fail, we still recover data from the checkpoint
      _ <- bakersProcessor.processBakersForBlocks(bakersCheckpoints)
      _ <- bakersProcessor.updateBakersBalances(blocks)
      rollsData <- nodeOperator.getBakerRollsForBlocks(blocks)
      _ <- processBlocksForGovernance(rollsData.toMap)
    } yield results.size

  }

  private def processTzip16Metadata(blocks: List[Block])(implicit ec: ExecutionContext): Future[Option[Int]] = {
    val internalTransactionResults = blocks.flatMap { block =>
      block.operationGroups.flatMap { opGroups =>
        opGroups.contents.collect {
          case TezosTypes.Transaction(_, _, _, _, _, _, _, _, _, metadata) =>
            metadata.internal_operation_results.toList.flatten.collect {
              case transaction: InternalOperationResults.Transaction => transaction
            }
        }
      }
    }.flatten

    val originatins = blocks.flatMap { block =>
      block.operationGroups.map { opGroups =>
        opGroups.contents.collect {
          case o:TezosTypes.Origination =>
            o
        }
      }
    }.flatten

    val xd = "MichelsonSingleInstruction:Pair::1;MichelsonType:Pair::0;MichelsonType:Pair::0;MichelsonInstructionSequence:0;MichelsonSingleInstruction:Elt::1"
    val origiRes = originatins.flatMap { orig =>
      orig.script.flatMap { x =>
        x.storage_micheline.map { xx =>
          orig -> Tzip16
            .extractTzip16MetadataLocationFromParameters(xx, None)
        }
      }.toList
    }.filter {
      case (_, location) => location.nonEmpty
    }.map(x => x._1 -> x._2.get)

    val xxx = metadataOperator.getMetadataWithOrigination(origiRes).map { result =>
      result.filter(x => x._2.isDefined && x._1._1.metadata.operation_result.originated_contracts.toList.flatten.nonEmpty).map {
        case ((transaction, location), Some((raw, meta))) =>
          (transaction.metadata.operation_result.originated_contracts.get.head.id, transaction.source.value, location, (raw, meta))
      }
    }

    val itrPathMichelinePairFut = internalTransactionResults.traverse { itr =>
      db.run(TezosDb.getContractMetadataPath(itr.destination.id)).map {
        case Some(path) =>
          itr.parameters_micheline.toList.map {
            case Left(value) => (itr, path, value.value)
            case Right(value) => (itr, path, value)
          }
        case None => List.empty
      }
    }.map(_.flatten)

    val metadata = itrPathMichelinePairFut.flatMap { pathMichelinePair =>
      val res = pathMichelinePair.map {
        case (itr, path, micheline) =>
          itr -> Tzip16
                .extractTzip16MetadataLocationFromParameters(micheline, Some(path))
      }.filter {
        case (_, location) => location.nonEmpty
      }.map(x => x._1 -> x._2.get)

      metadataOperator.getMetadataWithIntTransaction(res).map { result =>
        result.filter(_._2.isDefined).map {
          case ((transaction, location), Some((raw, meta))) => (transaction.destination.id, transaction.source.value, location, (raw, meta))
        }
      }
    }

    for {
      tok <- metadata
      org <- xxx
      sth = tok ::: org
      yyy <- db.run(TezosDb.writeTokenMetadata(sth))
    } yield yyy
  }

  /** Prepares and stores statistics for voting periods of the
    * blocks passed in.
    *
    * @param bakerRollsByBlock blocks of interest, with any rolls data available
    * @return the outcome of the operation, which may fail with an error or produce no result value
    */
  private def processBlocksForGovernance(bakerRollsByBlock: Map[Block, List[Voting.BakerRolls]])(
      implicit ec: ExecutionContext
  ): Future[Unit] =
    TezosGovernanceOperations
      .extractGovernanceAggregations(db, nodeOperator)(bakerRollsByBlock)
      .flatMap(aggregates => db.run(TezosDb.insertGovernance(aggregates)))
      .void

}
