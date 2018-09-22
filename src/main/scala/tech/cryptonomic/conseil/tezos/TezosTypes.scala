package tech.cryptonomic.conseil.tezos

/**
  * Classes used for deserializing Tezos node RPC results.
  */
object TezosTypes {

  case class BlockHash(value: String) extends AnyVal

  /** a conventional value to get the latest block in the chain */
  final lazy val blockHeadHash = BlockHash("head")

  case class BlockHeader(
                        level: Int,
                        proto: Int,
                        predecessor: BlockHash,
                        timestamp: java.sql.Timestamp,
                        validationPass: Int,
                        operations_hash: Option[String],
                        fitness: Seq[String],
                        context: String,
                        signature: Option[String]
                        )

  case class BlockMetadata(
                            protocol: String,
                            chain_id: Option[String],
                            hash: BlockHash,
                            header: BlockHeader
                  )

  case class OperationMetadata(
                              delegate: Option[String],
                              slots: Option[List[Int]],
                              balanceUpdates: Option[List[AppliedOperationBalanceUpdates]],
                              operationResult: Option[AppliedOperationResultStatus],
                              internalOperationResult: Option[AppliedInternalOperationResult]
                              )

  case class AppliedOperationBalanceUpdates(
                                             kind: String,
                                             contract: Option[String],
                                             change: Int,
                                             category: Option[String],
                                             delegate: Option[String],
                                             level: Option[Int]
                                           )

  case class AppliedOperationResultStatus(
                                   status: String,
                                   errors: Option[List[String]],
                                   storage: Option[Any],
                                   balanceUpdates: Option[AppliedOperationBalanceUpdates],
                                   originatedContracts: Option[String],
                                   consumedGas: Option[Int],
                                   storageSizeDiff: Option[Int]
                                   )

  case class AppliedInternalOperationResult(
                                           kind: String,
                                           source: String,
                                           nonce: Int,
                                           publicKey: Option[String],
                                           result: AppliedOperationResultStatus,
                                           amount: Option[Int],
                                           destination: Option[String],
                                           parameters: Option[Any],
                                           managerPubKey: Option[String],
                                           balance: Option[Int],
                                           spendable: Option[Boolean],
                                           delegatable: Option[Boolean],
                                           delegate: Option[String],
                                           script: Option[ScriptedContracts],
                                           )

  case class ScriptedContracts(
                              storage: Any,
                              code: Any
                              )

  case class InlinedEndorsement(
                               branch: String,
                               operation: InlinedEndorsementContents,
                               signature: Option[String]
                               )

  case class InlinedEndorsementContents(
                                       kind: String,
                                       block: String,
                                       level: String,
                                       slots: List[Int]
                                       )

  case class Operation(
                       kind: String,
                       block: Option[String],
                       level: Option[Int],
                       slots: Option[List[Int]],
                       nonce: Option[String],
                       op1: Option[InlinedEndorsement],
                       op2: Option[InlinedEndorsement],
                       bh1: Option[BlockHeader],
                       bh2: Option[BlockHeader],
                       pkh: Option[String],
                       secret: Option[String],
                       proposals: Option[List[String]],
                       period: Option[String],
                       source: Option[String],
                       proposal: Option[String],
                       ballot: Option[String],
                       fee: Option[String],
                       counter: Option[Int],
                       gasLimit: Option[String],
                       storageLimit: Option[String],
                       publicKey: Option[String],
                       amount: Option[String],
                       destination: Option[String],
                       parameters: Option[Any],
                       managerPubKey: Option[String],
                       balance: Option[String],
                       spendable: Option[Boolean],
                       delegatable: Option[Boolean],
                       delegate: Option[String]
                       )

  case class OperationGroup (
                              protocol: String,
                              chain_id: Option[String],
                              hash: String,
                              branch: String,
                              contents: Option[List[Operation]],
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

  case class AccountsWithBlockHashAndLevel(
                                    blockHash: BlockHash,
                                    blockLevel: Int,
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

  case class AppliedOperationError(
                                  kind: String,
                                  id: String,
                                  hash: String
                                  )

  case class AppliedOperationResult(
                                   operation: String,
                                   status: String,
                                   operationKind: Option[String],
                                   balanceUpdates: Option[List[AppliedOperationBalanceUpdates]],
                                   originatedContracts: Option[List[String]],
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
