package tech.cryptonomic.conseil.indexer.bitcoin.rpc.json

case class ScriptSig(
    asm: String,
    hex: String
)

case class TransactionInput(
    txid: Option[String],
    vout: Option[Int],
    scriptSig: Option[ScriptSig],
    sequence: Long,
    coinbase: Option[String],
    txinwitness: Option[Seq[String]]
) extends TransactionComponent
