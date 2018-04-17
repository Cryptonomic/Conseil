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

  case class BlockOperationMetadata(
                           hash: String,
                           branch: String,
                           data: String
                           )

  case class TezosScriptExpression(
                                  prim: Option[String],
                                  args: Option[List[String]]
                                  )

  case class Operation (
                       kind: String,
                       level: Option[Int],
                       nonce: Option[String],
                       op1: Option[Operation],
                       op2: Option[Operation],
                       //another op1/op2, worry about that later
                       id: Option[String],
                       public_key: Option[String],
                       amount: Option[scala.math.BigDecimal], //String?
                       destination: Option[String],
                       parameters: Option[Any], //michexp, figure out how to parse later
                       managerPubKey: Option[String],
                       balance: Option[scala.math.BigDecimal], //String?
                       spendable: Option[Boolean],
                       delegatable: Option[Boolean],
                       delegate: Option[String],
                       script: Option[Any]// michexp
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
                            counter: Option[scala.math.BigDecimal], //String?
                            fee: Option[String]  //String?
                            )

  case class OperationGroupContainer(
                                    ok: List[List[OperationGroup]]
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
                    script: String,//Option[Any],
                    counter: Int
                    )

  case class AccountContainer(
                             ok: Account
                             )

  case class AccountsContainer(
                     ok: List[String]
                     )

  case class AccountsWithBlockHash(
                                    block_hash: String,
                                    accounts: Map[String, Account]
                                  )

  case class Block(
                    metadata: BlockMetadata,
                    operationGroups: List[OperationGroup]
                  )

  case class ForgedOperationContainer(ok: Option[SuccessfulForgedOperation], error: Option[Any])

  case class SuccessfulForgedOperation(operation: String)

  case class AppliedOperationContainer(ok: Option[AppliedOperation], error: Option[Any])

  case class AppliedOperation(contracts: Array[String])

  case class InjectedOperationContainer(ok: Option[InjectedOperation], error: Option[Any])

  case class InjectedOperation(injectedOperation: String)

}
