# Conseil
Query API for the Tezos blockchain

## Running Conseil

Conseil has two entry points:
- `src/main/Conseil.scala` runs a server exposing a RESTful API.
- `src/main/Lorre.scala` is a background process that runs on loop and writes Tezos blockchain data to database.

### Warning 

The Conseil server should be run behind a proxy such as Nginx with TLS enabled through something like LetsEncrypt. Futhermore, [HTTP Strict Transport Security](https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security) and [Certification Authority Authorization](https://en.wikipedia.org/wiki/DNS_Certification_Authority_Authorization) are highly recommended!

### Prerequisites
- JDK
- Scala
- SBT
- A database supported by Typesafe Slick, e.g. Postgres

### Compile

`sbt compile`
  
### Package
 
`sbt assembly`
  
### Run

Run the fat JAR with the JRE:

`java -Dconfig.file={path to custom config file} -cp {path to fat JAR} tech.cryptonomic.conseil.Conseil`

Run locally using SBT:

` env SBT_OPTS="-Dconfig.file={path to custom config file}" sbt "runMain tech.cryptonomic.conseil.Conseil"`

### Custom configurations

It is advisable to run with a custom config file which inherits from the main or the “developer” conf file. Here is an example:

```json
include "developer"

conseildb = {
  dataSourceClass = "org.postgresql.ds.PGSimpleDataSource"
  properties = {
    databaseName = "conseil"
    user = "redacted"
    password = "redacted"
  }
  numThreads = 10
}

platforms: {
  tezos: {
    alphanet : {
      node: {
        hostname: "localhost",
        port: 8732
        pathPrefix: ""
      }
    }
  }
}
```
