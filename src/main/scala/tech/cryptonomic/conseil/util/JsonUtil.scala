package tech.cryptonomic.conseil.util

import com.fasterxml.jackson.core.{JsonParser, JsonParseException}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.annotation.tailrec
import scala.util.Try

import scala.util.matching.Regex

/**
  * Jackson wrapper for JSON serialization and deserialization functions.
  */
object JsonUtil {

  private val managerPubkeyMatcher = """("managerPubkey":)""".r
  private val managerPubkeyAdaptation = """"manager_pubkey":"""

  /** convert alphanet schema field name to the zeronet one */
  def adaptManagerPubkeyField(json: String): String = {
    managerPubkeyMatcher.replaceAllIn(json, replacement = managerPubkeyAdaptation)
  }

  /*
   * We're reducing visibility of the JsonString constuction (both class and object)
   * to allow instantiation only from JsonUtil's methods
   * The goal is to guarantee that only valid json will be contained within the value class wrapper
   */
  final case class JsonString private (json: String) extends AnyVal with Product with Serializable

  object JsonString {

    // Note: instead of making it private, it might make sense to verify the input
    // and return the [[JsonString]] within a wrapping effect (e.g. Option, Try, Either)
    private[JsonUtil] def apply(json: String): JsonString = new JsonString(json)

    /**
      * Creates a [[JsonString]] from a generic String, doing formal validation
      * @param s the "stringified" json
      * @return a valid JsonString or a failed [[Try]] with the parsing error
      */
    def wrapString(s: String): Try[JsonString] =
      Try {
        validate(mapper.getFactory.createParser(s))
      }.map(_ => JsonString(s))

    //verifies if the parser can proceed till the end
    @tailrec
    @throws[JsonParseException]("when content is not parseable, especially for not well-formed json")
    private def validate(parser: JsonParser): Boolean = {
      parser.nextToken == null || validate(parser)
    }

    /** A [[JsonString]] representing a json object with no attributes */
    lazy val emptyObject = JsonString("{}")

    /** add standard cleaning for input json */
    def sanitize(s: String): String = s.filterNot(_.isControl)

  }

  private val mapper = new ObjectMapper with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
      .disable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)

  def toJson[T](value: T): JsonString =
    JsonString(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))

  def toMap[V](json:String)(implicit m: Manifest[V]): Map[String, V] =
    fromJson[Map[String,V]](json)

  def fromJson[T: Manifest](json: String): T =
    mapper.readValue[T](JsonString sanitize json)

  /** extractor object to read accountIds from a json string, based on the hash format*/
  object AccountIds {
    /** regular expression matching a valid account hash as a json string */
    val AccountHashExpression: Regex = """"(tz[1-3]|KT1)[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{33}"""".r

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