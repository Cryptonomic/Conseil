package tech.cryptonomic.conseil.tezos

/**
  * Classes used for deserializing Tezos node RPC results.
  */
object TezosTypes {

  case class BlockMetadata(
                            hash: String,
                            chain_id: String,
                            protocol: String,
                            level: Int,
                            proto: Int,
                            predecessor: String,
                            timestamp: java.sql.Timestamp,
                            validation_pass: Int,
                            operations_hash: String,
                            fitness: Seq[String],
                            context: String,
                            protocol_data: String
                  )

  case class Operation (
                       kind: String,
                       level: Option[BigDecimal],
                       nonce: Option[String],
                       op1: Option[Operation],
                       op2: Option[Operation],
                       id: Option[String],
                       public_key: Option[String],
                       amount: Option[String],
                       destination: Option[String],
                       parameters: Option[Any],
                       managerPubKey: Option[String],
                       balance: Option[String],
                       spendable: Option[Boolean],
                       delegatable: Option[Boolean],
                       delegate: Option[String],
                       script: Option[Any]
                       )

  case class OperationGroup (
                            hash: String,
                            branch: String,
                            kind: Option[String],
                            block: Option[String],
                            level: Option[Int],
                            slots: Option[List[Int]],
                            signature: Option[String],
                            proposals: Option[String],
                            period: Option[BigDecimal],
                            source: Option[String],
                            proposal: Option[String],
                            ballot: Option[String],
                            chain: Option[String],
                            operations: Option[List[Operation]],
                            counter: Option[scala.math.BigDecimal],
                            fee: Option[String]
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

  case class SuccessfulForgedOperation(operation: String)

  case class AppliedOperation(contracts: Array[String])

  case class InjectedOperationContainer(ok: Option[InjectedOperation], error: Option[Any])

  case class InjectedOperation(injectedOperation: String)

}
