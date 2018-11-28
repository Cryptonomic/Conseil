# Conseil
Query API for the Tezos blockchain

[![Build Status](https://travis-ci.org/Cryptonomic/Conseil.svg?branch=master)](https://travis-ci.org/Cryptonomic/Conseil)

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

Or you can use the predefined tasks which are custom-tailored with VM launch configuration that's best suited for the actual task:
- for Conseil:
`sbt -Dconfig.file="{path to custom config file}" runConseil`
- for Lorre 
`sbt -Dconfig.file="{path to custom config file}" "runLorre alphanet"`
- for schema generation
`sbt -Dconfig.file="{path to custom config file}" genSchema`


### Locally test with database [`Docker installation needed]
The application expects to access a postgres database instance to run.

A `docker-compose.yml` file is included in the `root` directory that will launch a docker container with the database.  
You need to customize the credentials in the YAML file or in your custom configuration [see the following section]

To run the database, from the project root
```bash
docker-compose up -d
```
This will launch the db container and setup the schema as described by the file under `doc/conseil.sql`

To stop the database
```bash
docker-compose down
```
This will stop and remove the container, but will keep the db data in the `pgdata` project folder, 
so you can restart the container without losing any information stored.

To clean and restart the db from scratch, simply remove all `pgdata` content while the container is _not running_.

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
