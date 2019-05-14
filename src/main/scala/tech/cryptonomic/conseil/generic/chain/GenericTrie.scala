package tech.cryptonomic.conseil.generic.chain

class Trie extends GenericTrie[String] {
  def put(str: String): Unit = put(str, str)
}

class GenericTrie[V](key: Option[Char]) {

  var value: Option[V] = None

  import scala.collection.Seq
  import scala.collection.immutable.{TreeMap, WrappedString}

  private var nodes = new TreeMap[Char, GenericTrie[V]]

  def this() {
    this(None)
  }

  def put(k: String, v: V): Unit = {

    normalise(k) match {
      case Seq() =>
        this.value = Some(v)
      case Seq(h, t@_*) =>
        val node = this.nodes.get(h) match {
          case None =>
            val n = new GenericTrie[V](Some(h))
            this.nodes = this.nodes.insert(h, n)
            n

          case Some(n) => n
        }
        node.put(t, v)
    }
  }

  def get(k: String): Option[V] = {
    nodeFor(k).flatMap(_.value)
  }

  def getAllWithPrefix(k: String): Seq[V] = {
    nodeFor(k).toList.flatMap(_.getAll)
  }

  def getNWithPrefix(k: String, n: Int): Seq[V] = {
    lazy val res = getAllWithPrefix(k)
    res.take(n)
  }

  def getAll: Seq[V] = {
    this.value.toList ++ this.nodes.values.flatMap(_.getAll)
  }

  override def toString: String = {
    key.toString ++ ":" ++ value.toString ++ "\n" ++ nodes.values.map(_.toString.split("\n").map("  " + _).mkString("\n")).mkString("\n")
  }

  def nodeFor(k: String): Option[GenericTrie[V]] = {
    normalise(k) match {
      case Seq() => Some(this)
      case Seq(h, t@_*) => nodes.get(h).flatMap { n => n.nodeFor(t) }
    }
  }

  private def normalise(s: String): WrappedString = {
    new WrappedString(s.toLowerCase.replaceAll("""\W+""", ""))
  }

  implicit def charSeq2WrappedString(s: Seq[Char]): WrappedString = {
    new WrappedString(s.mkString)
  }

  implicit def charSeq2String(s: Seq[Char]): String = {
    s.mkString
  }

  implicit def catOptions[A](xs: Seq[Option[A]]): Seq[A] = {
    xs.flatMap(_.toList)
  }
}

