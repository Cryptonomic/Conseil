package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.Tables.Proposals

/**
  * Classes used for deserializing Tezos node RPC results.
  */
object TezosTypes {

  case class BlockMetadata(
                            hash: String,
                            chain_id: String,
                            operations: Seq[Seq[BlockOperationMetadata]],
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
  /*
  case class Operation (
                       hash: String,
                       branch: String,
                       kind: String,
                       block: Option[String],
                       level: Option[Int],
                       slots: Option[List[Int]],
                       signature: Option[String],
                       source: Option[String],
                       period: Option[Int],
                       proposals: Option[String],
                       ballot: Option[String],
                       fee: Option[Int],
                       counter: Option[Int],
                       operations: Option[List[OperationGroup]]
                       )

  case class OperationGroup (

                            )
  */
  //(Manager Operation) is considered an OperationGroup
  case class OperationGroup(
                    hash: String,
                    branch: String,
                    kind: String,
                    source: Option[String],
                    fee: Option[Int],
                    counter: Option[Int],
                    operations: List[ManagerOperation],
                    signature: Option[String]
                    )
  //Make ManagerOperation a Trait
  case class Transaction(
                        kind: String,
                        amount: Int,
                        destination: String,
                        //parameters is a Micheline expression, figure out how to parse later
                        parameters: Option[String]
                        )

  case class Delegation(
                       kind: String,
                       delegate: Option[String]
                       )

  case class Origination(
                        kind: String,
                        managerPubKey: String,
                        balance: Int,
                        spendable: Option[Boolean],
                        delegatable: Option[Boolean],
                        delegate: Option[String]
                        //script is a Micheline expression, figure out how to parse later
                        //script: Option[Any]
                        )

  case class Reveal(
                   kind: String,
                   publicKey: String
                   )

  case class ManagerOperation(
                             transaction: Option[Transaction],
                             delegation: Option[Delegation],
                             origination: Option[Origination],
                             reveal: Option[Reveal]
                             )

  case class Operation(
                      kind: Option[String],
                      amount: Option[scala.math.BigDecimal],
                      destination: Option[String],
                      //parameters: Option[Object],
                      managerPubKey: Option[String],
                      balance: Option[BigDecimal],
                      spendable: Option[Boolean],
                      delegatable: Option[Boolean],
                      delegate: Option[String],
                      //script: Option[Any],
                      block: Option[String],
                      slot: Option[Int],
                      period: Option[Int],
                      proposal: Option[String],
                      ballot: Option[String],
                      level: Option[Int],
                      nonce: Option[String],
                      id: Option[String]
                      )
/*
  case class OperationGroup(
                           hash: String,
                           branch: String,
                           kind: String,
                           block: String,
                           level: Int,
                           slots: List[Int],
                           //source: Option[String],
                           //operations: List[Operation],
                           signature: Option[String]
                           )
*/
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
                    script: Option[Any],
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
