package tech.cryptonomic.conseil.common.util

import io.circe._
import io.circe.parser.decode
import io.circe.syntax._
import scala.util.Try

import scala.util.matching.Regex

/**
  * Jackson wrapper for JSON serialization and deserialization functions.
  */
object JsonUtil {

  object CirceCommonDecoders {
    import io.circe.Decoder

    /** Provides deconding to an Either value, without needing a discrimination "tag" in the json source*/
    implicit def decodeUntaggedEither[A, B](
        implicit leftDecoder: Decoder[A],
        rightDecoder: Decoder[B]
    ): Decoder[Either[A, B]] =
      leftDecoder.map(Left.apply) or rightDecoder.map(Right.apply)
  }

  private val managerPubkeyMatcher = """("managerPubkey":)""".r
  private val managerPubkeyAdaptation = """"manager_pubkey":"""

  /** convert alphanet schema field name to the zeronet one */
  def adaptManagerPubkeyField(json: String): String =
    managerPubkeyMatcher.replaceAllIn(json, replacement = managerPubkeyAdaptation)

  /*
   * We're reducing visibility of the JsonString constuction (both class and object)
   * to allow instantiation only from JsonUtil's methods
   * The goal is to guarantee that only valid json will be contained within the value class wrapper
   */
  final case class JsonString private (json: String) extends AnyVal with Product with Serializable

  object JsonString {

    case class InvalidJsonString[T <: Throwable](cause: T) extends Throwable

    // Note: instead of making it private, it might make sense to verify the input
    // and return the [[JsonString]] within a wrapping effect (e.g. Option, Try, Either)
    private[JsonUtil] def apply(json: String): JsonString = new JsonString(json)

    /**
      * Creates a [[JsonString]] from a generic String, doing formal validation
      * @param s the "stringified" json
      * @return a valid JsonString or a failed [[Try]] with the parsing error
      */
    def fromString(s: String): Try[JsonString] =
      io.circe.parser
        .parse(s)
        .map(_ => JsonString(s))
        .left
        .map(InvalidJsonString(_))
        .toTry

    /** A [[JsonString]] representing a json object with no attributes */
    lazy val emptyObject = JsonString("{}")

    /** add standard cleaning for input json */
    def sanitize(s: String): String =
      s.filterNot(_.isControl)
        .replaceAll("""\\\\(u[a-zA-Z0-9]{1,4})""", "$1")
        .replaceAll("""\\(u[a-zA-Z0-9]{1,4})""", "$1")
  }

  def toJson[T: Encoder](value: T): JsonString =
    JsonString(value.asJson.spaces4)

  def toListOfMaps[V: Decoder](json: String): Try[List[Map[String, V]]] =
    fromJson[List[Map[String, V]]](json)

  def fromJson[T: Decoder](json: String): Try[T] = {
    val result = decode[T](JsonString sanitize json)

    result.left.foreach {
      case f @ ParsingFailure(msg, cause) =>
        println(
          s"Parsing failed for the following json string: $json. This is the error message $msg and the cause is ${cause.getMessage()}"
        )
      case f @ DecodingFailure(msg, history) =>
        println(
          s"Decoding to an object failed for the following json string: $json. This is the error message $msg and cursor operations so far: $history"
        )
    }

    result.toTry
  }

  /** extractor object to read accountIds from a json string, based on the hash format*/
  object AccountIds {

    /** regular expression matching a valid account hash as a json string */
    val AccountHashExpression: Regex =
      """"(tz[1-3]|KT1)[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{33}"""".r

    /** enables pattern matching with a variable number of matches */
    def unapplySeq(json: String): Option[List[String]] = {
      val matched = AccountHashExpression
        .findAllIn(json)
        .map(_.tail.dropRight(1)) //removes the additional quotes
      if (matched.isEmpty) None
      else Some(matched.toList)
    }
  }

}
