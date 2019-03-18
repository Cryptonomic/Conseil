coverageExcludedPackages := Seq(
    "<empty>",
    ".*\\.tezos.Tables",
    ".*\\.Lorre",
    ".*\\.Conseil",
    ".*config\\.LorreAppConfig",
    ".*config\\.ConseilAppConfig",
    ".*config\\.Security",
    "tech\\.cryptonomic\\.conseil\\.io.*",
    "tech\\.cryptonomic\\.conseil\\.scripts.*"
  ).mkString(";")
