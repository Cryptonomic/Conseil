package tech.cryptonomic.conseil.util

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
  * Jackson wrapper for JSON serialization and deserialization functions.
  */
object JsonUtil {

  final case class JsonString(json: String) extends AnyVal with Product with Serializable

  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def toJson(value: Map[Symbol, Any]): JsonString = {
    toJson(value map { case (k,v) => k.name -> v})
  }

  def toJson[T](value: T): JsonString = {
    JsonString(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))
  }

  def toMap[V](json:String)(implicit m: Manifest[V]): Map[String, V] = fromJson[Map[String,V]](json)

  def fromJson[T](json: String)(implicit m : Manifest[T]): T = {
    mapper.readValue[T](json.filterNot(Character.isISOControl))
  }
}