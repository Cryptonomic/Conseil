package tech.cryptonomic.conseil.tezos

/**
  * Classes used for deserializing Tezos node RPC results.
  */
object TezosTypes {

  case class BlockHeader(
                        level: Int,
                        proto: Int,
                        predecessor: String,
                        timestamp: java.sql.Timestamp,
                        validation_pass: Int,
                        operations_hash: String,
                        fitness: Seq[String],
                        context: String,
                        priority: Int,
                        proof_of_work_nonce: String,
                        signature: String
                        )

  case class BlockMetadata(
                            protocol: String,
                            chain_id: String,
                            hash: String,
                            header: BlockHeader,
                            metadata: Any,
                  )

  //fix this!!!
  case class OperationMetadata(
                              delegate: Option[String],
                              slots: Option[List[Int]],
                              balanceUpdates: Option[List[AppliedOperationBalanceUpdates]],
                              operationResult: Option[AppliedOperationResult],
                              internalOperationResult: Option[AppliedOperationResult]
                              )

  case class InlinedEndorsement(
                               branch: String,
                               operation: Operation,
                               signature: Option[String]
                               )

  case class Operation (
                       kind: String,
                       block: Option[String],
                       level: Option[Int],
                       slots: Option[List[Int]],
                       metadata: OperationMetadata,
                       nonce: Option[String],
                       op1: Option[InlinedEndorsement],
                       op2: Option[InlinedEndorsement],
                       bh1: Option[BlockHeader],
                       bh2: Option[BlockHeader],
                       pkh: Option[String],
                       secret: Option[String],
                       proposals: Option[List[String]],
                       period: Option[Int],
                       source: Option[String],
                       proposal: Option[String],
                       ballot: Option[String], //ballot case class? nay, yay, pass
                       fee: Option[Int], //mutez
                       counter: Option[Int],
                       gasLimit: Option[Int],
                       storageLimit: Option[Int],
                       publicKey: Option[String],
                       amount: Option[Int], //mutez
                       destination: Option[String],
                       parameters: Option[Any], //michExp
                       managerPubKey: Option[String],
                       balance: Option[Int], // mutez
                       spendable: Option[Boolean],
                       delegatable: Option[Boolean],
                       delegate: Option[String]
                       )

  case class OperationGroup (
                            protocol: String,
                            chain_id: String,
                            hash: String,
                            branch: String,
                            operations: Option[List[Operation]],
                            signature: Option[String],
                            )

  case class AccountDelegate(
                            setable: Boolean,
                            value: Option[String]
                            )

  case class Account(
                    manager: String,
                    balance: scala.math.BigDecimal,
                    spendable: Boolean,
                    delegate: AccountDelegate,
                    script: Option[Any],
                    counter: Int
                    )

  case class AccountsWithBlockHash(
                                    block_hash: String,
                                    accounts: Map[String, Account]
                                  )

  case class Block(
                    metadata: BlockMetadata,
                    operationGroups: List[OperationGroup]
                  )

  case class ManagerKey(
                       manager: String,
                       key: Option[String]
                       )

  case class ForgedOperation(operation: String)

  case class AppliedOperationBalanceUpdates(
                                           kind: String,
                                           contract: String,
                                           debited: Option[String],
                                           credited: Option[String]
                                           )

  case class AppliedOperationError(
                                  kind: String,
                                  id: String,
                                  hash: String
                                  )

  case class AppliedOperationResult(
                                   operation: String,
                                   status: String,
                                   operation_kind: Option[String],
                                   balance_updates: Option[List[AppliedOperationBalanceUpdates]],
                                   originated_contracts: Option[List[String]],
                                   errors: Option[List[AppliedOperationError]]
                                   )

  case class AppliedOperation(
                             kind: String,
                             balance_updates: Option[List[AppliedOperationBalanceUpdates]],
                             operation_results: Option[List[AppliedOperationResult]],
                             id: Option[String],
                             contract: Option[String]
                             )

  case class InjectedOperation(injectedOperation: String)

  case class MichelsonExpression(prim: String, args: List[String])

}
