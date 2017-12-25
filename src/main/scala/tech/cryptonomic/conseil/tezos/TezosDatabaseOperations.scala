package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.TezosTypes.Block

object TezosDatabaseOperations {

  def writeToDatabase(blocks: List[Block], dbHandle: Database) =
    dbHandle.run(
      DBIO.seq(
        Tables.Blocks ++= blocks.map(blockToDatabaseRow)
      )
    )

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
      fitness1 = block.metadata.fitness.head,
      fitness2 = block.metadata.fitness.tail.head,
      data = block.metadata.data,
      hash = block.metadata.hash
    )

}
