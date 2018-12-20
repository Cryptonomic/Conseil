package tech.cryptonomic.conseil.util

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.util.matching.Regex

/**
  * Jackson wrapper for JSON serialization and deserialization functions.
  */
object JsonUtil {

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

    /** A [[JsonString]] representing a json object with no attributes */
    lazy val emptyObject = JsonString("{}")

  }

  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def toJson[T](value: T): JsonString = {
    JsonString(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))
  }

  def toMap[V](json:String)(implicit m: Manifest[V]): Map[String, V] = fromJson[Map[String,V]](json)

  def fromJson[T](json: String)(implicit m : Manifest[T]): T = {
    mapper.readValue[T](json.filterNot(Character.isISOControl))
  }

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