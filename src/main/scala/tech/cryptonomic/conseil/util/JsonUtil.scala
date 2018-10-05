package tech.cryptonomic.conseil.util

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule

/**
  * Jackson wrapper for JSON serialization and deserialization functions.
  */
object JsonUtil {

  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  def toJson(value: Map[Symbol, Any]): String = {
    toJson(value map { case (k,v) => k.name -> v})
  }

  def toJson[T](value: T): String = {
    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
  }

  def toMap[V](json:String)(implicit m: Manifest[V]): Map[String, V] = fromJson[Map[String,V]](json)

  def fromJson[T](json: String)(implicit m : Manifest[T]): T = {
    mapper.readValue[T](json.filterNot(Character.isISOControl))
  }
}