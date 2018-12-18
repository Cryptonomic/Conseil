package tech.cryptonomic.conseil.tezos

/**
  * Classes used for deserializing Tezos node RPC results.
  */
object TezosTypes {

  /** convenience alias to simplify declarations of block hash+level tuples */
  type BlockReference = (BlockHash, Int)

  final case class BlockHash(value: String) extends AnyVal

  final case class OperationHash(value: String) extends AnyVal

  final case class AccountId(id: String) extends AnyVal

  /** a conventional value to get the latest block in the chain */
  final lazy val blockHeadHash = BlockHash("head")

  final case class BlockHeader(
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

  final case class BlockMetadata(
                            protocol: String,
                            chain_id: Option[String],
                            hash: BlockHash,
                            header: BlockHeader
                  )

  final case class OperationMetadata(
                              delegate: Option[String],
                              slots: Option[List[Int]],
                              balanceUpdates: Option[List[AppliedOperationBalanceUpdates]],
                              operationResult: Option[AppliedOperationResultStatus],
                              internalOperationResult: Option[AppliedInternalOperationResult]
                              )

  final case class AppliedOperationBalanceUpdates(
                                             kind: String,
                                             contract: Option[String],
                                             change: Int,
                                             category: Option[String],
                                             delegate: Option[String],
                                             level: Option[Int]
                                           )

  final case class AppliedOperationResultStatus(
                                   status: String,
                                   errors: Option[List[String]],
                                   storage: Option[Any],
                                   balanceUpdates: Option[AppliedOperationBalanceUpdates],
                                   originatedContracts: Option[String],
                                   consumedGas: Option[Int],
                                   storageSizeDiff: Option[Int]
                                   )

  final case class AppliedInternalOperationResult(
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

  final case class ScriptedContracts(
                              storage: Any,
                              code: Any
                              )

  final case class InlinedEndorsement(
                               branch: String,
                               operation: InlinedEndorsementContents,
                               signature: Option[String]
                               )

  final case class InlinedEndorsementContents(
                                       kind: String,
                                       block: String,
                                       level: String,
                                       slots: List[Int]
                                       )

  final case class Operation(
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

  final case class OperationGroup (
                              protocol: String,
                              chain_id: Option[String],
                              hash: OperationHash,
                              branch: String,
                              contents: Option[List[Operation]],
                              signature: Option[String],
                            )

  final case class AccountDelegate(
                            setable: Boolean,
                            value: Option[String]
                            )

  final case class Account(
                    manager: String,
                    balance: scala.math.BigDecimal,
                    spendable: Boolean,
                    delegate: AccountDelegate,
                    script: Option[Any],
                    counter: Int
                    )

  final case class BlockAccounts(
                    blockHash: BlockHash,
                    blockLevel: Int,
                    accounts: Map[AccountId, Account] = Map.empty
                  )

  final case class Block(
                    metadata: BlockMetadata,
                    operationGroups: List[OperationGroup]
                  )

  final case class ManagerKey(
                       manager: String,
                       key: Option[String]
                       )

  final case class ForgedOperation(operation: String)

  final case class AppliedOperationError(
                                  kind: String,
                                  id: String,
                                  hash: String
                                  )

  final case class AppliedOperationResult(
                                   operation: String,
                                   status: String,
                                   operationKind: Option[String],
                                   balanceUpdates: Option[List[AppliedOperationBalanceUpdates]],
                                   originatedContracts: Option[List[String]],
                                   errors: Option[List[AppliedOperationError]]
                                   )

  final case class AppliedOperation(
                             kind: String,
                             balance_updates: Option[List[AppliedOperationBalanceUpdates]],
                             operation_results: Option[List[AppliedOperationResult]],
                             id: Option[String],
                             contract: Option[String]
                             )

  final case class InjectedOperation(injectedOperation: String)

  final case class MichelsonExpression(prim: String, args: List[String])

}
