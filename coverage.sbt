coverageExcludedPackages := Seq(
  "<empty>",
  ".*\\.tezos.Tables",
  ".*\\.Lorre",
  ".*\\.Conseil",
  ".*\\.LorreAppConfig",
  ".*\\.ConseilAppConfig",
  ".*config\\.Security",
  "tech\\.cryptonomic\\.conseil\\.common\\.io.*",
  "tech\\.cryptonomic\\.conseil\\.common\\.scripts.*"
).mkString(";")
