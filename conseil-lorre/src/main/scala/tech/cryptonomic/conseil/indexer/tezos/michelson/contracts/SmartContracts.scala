package tech.cryptonomic.conseil.indexer.tezos.michelson.contracts

import java.nio.charset.StandardCharsets
import scorex.util.encode.{Base16 => Hex}
import org.bouncycastle.jcajce.provider.digest.Blake2s.Blake2s128
import cats.data.Chain
import cats.implicits._

/** Generic utilities related to smart contracts on tezos */
object SmartContracts {

  val keywords = Set('parameter, 'storage, 'code)

  /** Takes the content of a smart contract script and creates
    * hex string via consistent hashing of the content.
    *
    * Allows to easily identify the contract type for different accounts,
    * especially for the most common and standardized ones.
    *
    * @param script contained in the contract account, should be in michelson format
    * @return the hash value as an hex-string
    */
  def hashMichelsonScript(script: String): String = {
    /* 1. pre-process and get the three parts
     * 2. join the parts with newline char and blake2s encode
     * 3. hex-encode the result
     */

    val preProcessed = preProcessScript(script)
    val sortedParts = preProcessed.getOrElse('parameter, "") ::
          preProcessed.getOrElse('storage, "") ::
          preProcessed.getOrElse('code, "") :: Nil
    hashOf(sortedParts.mkString("\n"))
  }

  /** The actual hash encoder */
  private val hashOf: String => String = in => {
    val blake2s = new Blake2s128()
    blake2s.update(in.getBytes(StandardCharsets.UTF_8))
    Hex.encode(blake2s.digest())
  }

  /** Remove unwanted characters and create a uniform layout for the contract parts,
    * i.e. code, parameters, storage.
    * Makes sure that contract hashing is consistent.
    *
    * @param script the michelson code as a raw string
    * @return the individual pieces, indexed by a symbol
    */
  private def preProcessScript(script: String): Map[Symbol, String] = {
    /* We clean up whitespaces and comments, and index lines progressively by matching leading keywords.
     * We fold over the lines, keeping track of state with a partial result map and the current keyword.
     * As we fold the keyword to line pair is added to the map using cats apis.
     * The monoidal operator |+| appends together entries under the same key of two maps, and returns
     * the resulting combined map, which is quite useful for our case.
     */

    /* checks for a keyword */
    def startingKeyword(line: String) = keywords.find(sym => line.startsWith(sym.name))

    def stripTralingComments(line: String) = line.replaceFirst("""\#[\s\S]+$""", "").trim()

    //clean up
    val lines = script.linesIterator
      .map(stripTralingComments)
      .filter(_.nonEmpty)
      .dropWhile(
        startingKeyword(_).isEmpty
      )

    if (lines.isEmpty) Map.empty
    else {
      //the iterator can only be consumed once, so we sequence it into a lazy stream
      val stream = lines.toStream
      /* we prepare the initial state and then fold */
      val keyword = startingKeyword(stream.head).getOrElse('impossible)
      val initialState = keyword -> Map(keyword -> Chain.one(stream.head))
      val (_, collected) =
        stream.tail
          .foldLeft(initialState) {
            case ((kw, collector), line) =>
              val nextKeyword = startingKeyword(line).getOrElse(kw)
              val updated = collector |+| Map(nextKeyword -> Chain.one(line))
              nextKeyword -> updated
          }

      collected.mapValues(_.mkString_(" "))
    }
  }

}
