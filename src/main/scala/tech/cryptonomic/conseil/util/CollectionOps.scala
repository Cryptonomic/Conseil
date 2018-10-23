package tech.cryptonomic.conseil.util

object CollectionOps {

  /**
    * provides common group opearaion for collections of 2-tuples
    * aggregating by the first element of the tuple as a key
    */
  def groupByKey[K, V](pairedSeq: Seq[(K, V)]): Map[K, Seq[V]] =
    pairedSeq.groupBy {
      case (k, v) => k
    }.mapValues {
      pairs => pairs.map {
        case (k, vs) => vs
      }
    }

  implicit class KeyedSeq[K, V](seq: Seq[(K, V)]) {
    def byKey(): Map[K, Seq[V]] = groupByKey(seq)
  }


}
