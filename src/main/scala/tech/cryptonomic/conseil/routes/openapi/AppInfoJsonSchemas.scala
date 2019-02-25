package tech.cryptonomic.conseil.routes.openapi

import endpoints.{algebra, generic}
import tech.cryptonomic.conseil.routes.AppInfo.Info

/** Trait containing AppInfo schema */
trait AppInfoJsonSchemas extends algebra.JsonSchemas with generic.JsonSchemas {
  /** AppInfo JSON schema */
  implicit def appInfoSchema: JsonSchema[Info] =
    genericJsonSchema[Info]
}
