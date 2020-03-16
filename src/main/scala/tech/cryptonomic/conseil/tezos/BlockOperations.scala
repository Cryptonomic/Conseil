package tech.cryptonomic.conseil.tezos
import TezosTypes.{
  Block,
  Delegation,
  InternalOperationResults,
  Operation,
  OperationsGroup,
  Origination,
  Reveal,
  Transaction
}

object BlockOperations {

  /**  Utility extractor that collects, for a block, both operations and internal operations results, grouped
    * in a form more amenable to processing
    * @param block the block to inspect
    * @return a Map holding for each group both external and internal operations' results
    */
  def extractOperationsAlongWithInternalResults(
      block: Block
  ): Map[OperationsGroup, (List[Operation], List[InternalOperationResults.InternalOperationResult])] =
    block.operationGroups.map { group =>
      val internal = group.contents.flatMap { op =>
        op match {
          case r: Reveal => r.metadata.internal_operation_results.toList.flatten
          case t: Transaction => t.metadata.internal_operation_results.toList.flatten
          case o: Origination => o.metadata.internal_operation_results.toList.flatten
          case d: Delegation => d.metadata.internal_operation_results.toList.flatten
          case _ => List.empty
        }
      }
      group -> (group.contents, internal)
    }.toMap
}
