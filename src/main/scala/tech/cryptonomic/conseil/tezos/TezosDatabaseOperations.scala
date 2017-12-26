package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.TezosTypes.{Block, BlockMetadata}

import scala.concurrent.Future

object TezosDatabaseOperations {

  def writeToDatabase(blocks: List[Block], dbHandle: Database): Future[Unit] = {
    dbHandle.run(
      DBIO.seq(
        Tables.Blocks ++= blocks.map(blockToDatabaseRow),
        Tables.OperationGroups ++= blocks.flatMap(operationGroupToDatabaseRow),
        Tables.Transactions ++= blocks.flatMap(transactionsToDatabaseRows),
        Tables.Endorsements ++= blocks.flatMap(endorsementsToDatabaseRows)
      )
    )
  }

  private def blockToDatabaseRow(block: Block): Tables.BlocksRow =
    Tables.BlocksRow(
      netId = block.metadata.net_id,
      protocol = block.metadata.protocol,
      level = block.metadata.level,
      proto = block.metadata.proto,
      //predecessor = Some(block.metadata.predecessor),
      predecessor = None,
      timestamp = Some(block.metadata.timestamp),
      validationPass = block.metadata.validation_pass,
      operationsHash = block.metadata.operations_hash,
      data = block.metadata.data,
      hash = block.metadata.hash,
      fitness = block.metadata.fitness.mkString(",")
    )

  private def operationGroupToDatabaseRow(block: Block): List[Tables.OperationGroupsRow] =
    block.operationGroups.map{ og =>
      Tables.OperationGroupsRow(
        hash = og.hash,
        blockId = block.metadata.hash,
        branch = og.branch,
        source = og.source,
        signature = og.signature
      )
    }

  private def transactionsToDatabaseRows(block: Block): List[Tables.TransactionsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="transaction").map{transaction =>
        Tables.TransactionsRow(
          transactionId = 0,
          operationGroupHash = og.hash,
          amount = transaction.amount.get,
          destination = transaction.destination,
          parameters = None
        )
      }
    }

  private def endorsementsToDatabaseRows(block: Block): List[Tables.EndorsementsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="endorsement").map{transaction =>
        Tables.EndorsementsRow(
          endorsementId = 0,
          operationGroupHash = og.hash,
          blockId = transaction.block.get,
          slot = transaction.slot.get
        )
      }
    }
}
