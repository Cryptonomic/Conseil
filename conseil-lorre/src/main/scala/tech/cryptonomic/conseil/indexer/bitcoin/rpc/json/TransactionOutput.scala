package tech.cryptonomic.conseil.indexer.bitcoin.rpc.json

case class ScriptPubKey(
    asm: String,
    hex: String,
    reqSigs: Option[Int],
    `type`: String,
    addresses: Option[Seq[String]]
)

case class TransactionOutput(
    txid: Option[String],
    value: Option[Double],
    n: Int,
    scriptPubKey: ScriptPubKey
) extends TransactionComponent
