package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshalling.{PredefinedToEntityMarshallers, ToEntityMarshaller}
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.server.Directives._

import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.util.JsonUtil.{toJson, JsonString}

object AppInfo {

  case class Info(application: String, version: String)

  //add the correct content-type for [[JsonUtil]]-converted values
  implicit private val jsonMarshaller: ToEntityMarshaller[JsonString] =
    PredefinedToEntityMarshallers.StringMarshaller
      .compose((_: JsonString).json)
      .wrap(MediaTypes.`application/json`)(identity _)

  val route: Route = pathEnd {
    get {
      complete(toJson(Info(application = BuildInfo.name, version = BuildInfo.version)))
    }
  }
}