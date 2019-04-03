package tech.cryptonomic.conseil.routes.openapi

import endpoints.generic
import tech.cryptonomic.conseil.routes.AppInfo.Info

/** Trait containing AppInfo schema */
trait AppInfoJsonSchemas extends generic.JsonSchemas {
  /** AppInfo JSON schema */
  implicit def appInfoSchema: JsonSchema[Info] =
    genericJsonSchema[Info]
}
