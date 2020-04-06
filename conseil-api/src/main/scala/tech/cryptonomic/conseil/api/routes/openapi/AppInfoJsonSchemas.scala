package tech.cryptonomic.conseil.api.routes.openapi

import endpoints.generic
import tech.cryptonomic.conseil.api.routes.AppInfo.{GitInfo, Info}

/** Trait containing AppInfo schema */
trait AppInfoJsonSchemas extends generic.JsonSchemas {

  /** AppInfo JSON schema */
  implicit lazy val appInfoSchema: JsonSchema[Info] =
    genericJsonSchema[Info]

  /** GitInfo JSON schema */
  implicit lazy val gitInfoSchema: JsonSchema[GitInfo] =
    genericJsonSchema[GitInfo]
}
