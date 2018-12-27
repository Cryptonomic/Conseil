package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshalling.{PredefinedToEntityMarshallers, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.server.Directives._

import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.util.JsonUtil.{toJson, JsonString}

/** defines endpoints to expose the currently deployed application information (e.g version, ...) */
object AppInfo {

  /** data type collecting relevant information to expose */
  case class Info(application: String, version: String)

  //add the correct content-type for [[JsonUtil]]-converted values
  implicit private val jsonMarshaller: ToEntityMarshaller[JsonString] =
    PredefinedToEntityMarshallers.StringMarshaller
      .compose((_: JsonString).json)
      .wrap(MediaTypes.`application/json`)(identity _)

  /** the endpoints to expose application information through http */
  val route: Route = pathEnd {
    get {
      complete(toJson(Info(application = BuildInfo.name, version = BuildInfo.version)))
    }
  }
}