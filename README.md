# Conseil
Query API for the Tezos blockchain

[![Build Status](https://travis-ci.org/Cryptonomic/Conseil.svg?branch=master)](https://travis-ci.org/Cryptonomic/Conseil)
[![Coverage Status](https://coveralls.io/repos/github/Cryptonomic/Conseil/badge.svg?branch=master)](https://coveralls.io/github/Cryptonomic/Conseil?branch=master)

## Running Conseil

Conseil has two entry points:
- `src/main/tech/cryptonomic/Conseil/Conseil.scala` runs a server exposing a RESTful API.
- `src/main/tech/cryptonomic/Conseil/Lorre.scala` is a background process that runs on loop and writes Tezos blockchain data to database.

### Warning

The Conseil server should be run behind a proxy such as Nginx with TLS enabled through something like LetsEncrypt. Futhermore, [HTTP Strict Transport Security](https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security) and [Certification Authority Authorization](https://en.wikipedia.org/wiki/DNS_Certification_Authority_Authorization) are highly recommended!

### Prerequisites

Development
- JDK (>7.x)
- Scala (>2.11.x)
- SBT (>1.2.6)
- A database supported by Typesafe Slick, e.g. Postgres

Deployment
- JRE (>7.x)
- A database supported by Typesafe Slick, e.g. Postgres

### Building, packing and deploying Conseil

#### Compile

Run the following command from the root directory of the repository:

`sbt compile`

#### Package

Run the following command from the root directory of the repository:

`sbt assembly`

#### Deploy

After the package step, check the SBT output for the path of the generated fat JAR file. Copy this file to the desired deployment area.

### Running Locally

(network is any field defined under platforms.<blockchain> in either application.conf. It is an object with the protocol, hostname, port, and pathPrefix of where your node is hosted. You can manually add your own in a local config file, as well)

Run the fat JAR with the JRE:

`java -Dconfig.file={path to custom config file} -cp {path to fat JAR} tech.cryptonomic.conseil.Conseil`

And..

`java -Dconfig.file={path to custom config file} -cp {path to fat JAR} tech.cryptonomic.conseil.Lorre <network>`

Run locally using SBT:

` env SBT_OPTS="-Dconfig.file={path to custom config file}" sbt "runMain tech.cryptonomic.conseil.Conseil"`

And..

` env SBT_OPTS="-Dconfig.file={path to custom config file}" sbt "runMain tech.cryptonomic.conseil.Lorre <network>"`

See 'Custom Configurations' section for information about custom config files.

### Database set up

The application expects to read and write from a database compatible with [Typesafe Slick](http://slick.lightbend.com/). One can either run against a database installed on the local system or, as described below, against a containerized database instance. In any case, the instructions in the 'Custom configurations' section below should be used to provide the correct database parameters to Conseil.

#### Using a database instance

Cryptonomic uses Postgres for all its Conseil deployments. Once a Postgres database is set up, `doc/conseil.sql` can be used to set up the latest schema. For non-Postgres databases, the schema file might have to be updated to reflect the idiosyncrasies of the particular SQL dialect being used.
Or you can use the predefined tasks which are custom-tailored with VM launch configuration that's best suited for the actual task:
- for Conseil:
`sbt -Dconfig.file="{path to custom config file}" runConseil`
- for Lorre
`sbt -Dconfig.file="{path to custom config file}" "runLorre alphanet"`
- for schema generation
`sbt -Dconfig.file="{path to custom config file}" genSchema`


### Locally test with database [`Docker installation needed]
The application expects to access a postgres database instance to run.

#### Using a Docker image for Postgres
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

### Running in production

Build Conseil as a fat JAR file using the 'Package' step above.

Run this command using a script or as part of a service definition for Conseil:

`java -Dconfig.file={path to custom config file} -cp {path to fat JAR file} tech.cryptonomic.conseil.Conseil`

Then, run this command using a script or as part of a service definition for Lorre:

`java -Dconfig.file={path to custom config file} -cp {path to fat JAR file} tech.cryptonomic.conseil.Lorre`

See 'Custom Configurations' section for information about custom config files.

### Custom configurations

Conseil uses [Typesafe Config](https://github.com/lightbend/config) and [Slick](http://slick.lightbend.com/doc/3.2.0/database.html) for managing its configurations. Please ensure you become familiar with both configuration systems before deploying Conseil. It is advisable to run with a custom config file which inherits from `src/main/resources/application.conf` for production or `src/main/resources/developer.conf` for local development.

Here is an example showing a default configuration used with custom database and Tezos node settings:

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

Save this file in <path to Conseil>/Conseil/src/main/resources

## Publishing the artifacts
If you're a contributor and need to publish the artifacts on sonatype, you'll find instructions in the [publishing doc](doc/publishing.md)
