package tech.cryptonomic.conseil.api.routes.info

import endpoints.generic
import AppInfo.{GitInfo, Info}

/** Trait containing AppInfo schema */
private[info] trait AppInfoJsonSchemas extends generic.JsonSchemas {

  /** AppInfo JSON schema */
  implicit lazy val appInfoSchema: JsonSchema[Info] =
    genericJsonSchema[Info]

  /** GitInfo JSON schema */
  implicit lazy val gitInfoSchema: JsonSchema[GitInfo] =
    genericJsonSchema[GitInfo]
}
