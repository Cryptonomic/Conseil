package tech.cryptonomic.conseil.common.bitcoin.rpc.json

case class ScriptPubKey(
    asm: String,
    hex: String,
    reqSigs: Option[Int],
    `type`: String,
    addresses: Option[Seq[String]]
)

case class TransactionOutput(
    txid: Option[String],
    value: Option[BigDecimal],
    n: Int,
    scriptPubKey: ScriptPubKey
) extends TransactionComponent
