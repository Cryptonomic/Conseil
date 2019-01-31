package tech.cryptonomic.conseil.tezos

import scala.math.BigDecimal

object DBTableMapping {

  /** A class useful to obviate for slick's encoding of a
    * operation row as a HList object, whose fields are not
    * as easy to extract, if not by position
    * Any Query result from Tables.OperationsRow can be converted
    * to this representation by using `.mapTo[Model.Operation]`
    */
  case class Operation(
    operationId: Int,
    operationGroupHash: String,
    kind: String,
    level: Option[Int] = None,
    delegate: Option[String] = None,
    slots: Option[String] = None,
    nonce: Option[String] = None,
    pkh: Option[String] = None,
    secret: Option[String] = None,
    source: Option[String] = None,
    fee: Option[BigDecimal] = None,
    counter: Option[BigDecimal] = None,
    gasLimit: Option[BigDecimal] = None,
    storageLimit: Option[BigDecimal] = None,
    publicKey: Option[String] = None,
    amount: Option[BigDecimal] = None,
    destination: Option[String] = None,
    parameters: Option[String] = None,
    managerPubkey: Option[String] = None,
    balance: Option[BigDecimal] = None,
    spendable: Option[Boolean] = None,
    delegatable: Option[Boolean] = None,
    script: Option[String] = None,
    status: Option[String] = None,
    consumedGas: Option[BigDecimal] = None,
    blockHash: String,
    blockLevel: Int,
    timestamp: java.sql.Timestamp
  )


}