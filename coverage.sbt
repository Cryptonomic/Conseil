coverageExcludedPackages := Seq(
  "<empty>",
  ".*\\.tezos.Tables",
  ".*\\directives.EnableCORSDirectives",
  ".*\\.routes.Docs",
  ".*\\.routes.openapi.TezosEndpoints",
  ".*\\.routes.openapi.OpenApiDoc",
  ".*\\.Lorre",
  ".*\\.Conseil",
  ".*config\\.LorreAppConfig",
  ".*config\\.ConseilAppConfig",
  ".*config\\.Security",
  "tech\\.cryptonomic\\.conseil\\.io.*",
  "tech\\.cryptonomic\\.conseil\\.scripts.*"
).mkString(";")
