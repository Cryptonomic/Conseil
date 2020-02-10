# Conseil
Query API for the Tezos blockchain

[![Build Status](https://travis-ci.org/Cryptonomic/Conseil.svg?branch=master)](https://travis-ci.org/Cryptonomic/Conseil)
[![Coverage Status](https://coveralls.io/repos/github/Cryptonomic/Conseil/badge.svg?branch=master)](https://coveralls.io/github/Cryptonomic/Conseil?branch=master)

## Running Conseil


### Docker

To build this project git clone it and run:

```
docker build -t conseil .
```

The Container can be configured using the following environment variables:

Database Config:


* DB_Host - default: db
* DB_User - default: user
* DB_Password - default: password
* DB_Database: default: conseil



Tezos Node Config:


* XTZ_Scheme - http or https, default : http
* XTZ_Host - default: node
* XTZ_Prefix - prefix for api calls, default: ""
* XTZ_Port - default 8732
* XTZ_Network - default mainnet


Conseil Config:


* API_PORT - Conseil API port, default: 80
* API_KEY - Conseil API key, default: conseil

Or, you can use your own config file, in which case specify the environment variable `CONFIG` with the path to your file

To run Lorre ( the indexer ) run:

```
docker run conseil lorre
```

and Conseil ( the API ):

```
docker run conseil conseil
```


## Running Conseil - classic


Conseil has two entry points:
- `src/main/tech/cryptonomic/Conseil/Conseil.scala` runs a server exposing a RESTful API over blockchain data, stored in its own database.
- `src/main/tech/cryptonomic/Conseil/Lorre.scala` is a background process that loops and synchronizes (Tezos) blockchain data to database.

### Warning

The Conseil server should be run behind a proxy such as Nginx with TLS enabled through something like LetsEncrypt. Futhermore, [HTTP Strict Transport Security](https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security) and [Certification Authority Authorization](https://en.wikipedia.org/wiki/DNS_Certification_Authority_Authorization) are highly recommended!

### Prerequisites

Development
- JDK (> 8.x)
- Scala (> 2.12.x)
- SBT (> 1.2.6)
- A database supported by Typesafe Slick, e.g. Postgres

Deployment
- JRE (> 8.x)
- A database supported by Typesafe Slick, e.g. Postgres

## Building, packaging and deploying Conseil
### Package
Run the following command from the root directory of the repository:

```bash
sbt clean assembly -J-Xss32m
```

This will clean any existing local artifact, compile the project, and create a "fat JAR" with the runnable application, including all its dependencies.
### Deploy

After the packaging step, check the SBT output for the path of the generated fat JAR file. Copy this file to the desired deployment area.

### Running Locally

#### Run the packaged fat JAR with the JRE:
After deploying the jar and having started a database instance [see the 'Database set-up' section]

```bash
java -Dconfig.file={path to custom config file} -cp {path to fat JAR} tech.cryptonomic.conseil.Conseil
```

And..

```bash
java -Dconfig.file={path to custom config file} -cp {path to fat JAR} tech.cryptonomic.conseil.Lorre <network>
```

#### Run locally using SBT:
From the project root and having started a database instance [see the 'Database set-up' section]

```bash
env SBT_OPTS="-Dconfig.file={path to custom conseil config file}" && sbt "runConseil"
```

And..

```bash
env SBT_OPTS="-Dconfig.file={path to custom lorre config file}" && sbt "runLorre <network>"
```

Here `network` refers to a valid configuration key, defined in the `.conf` file (any custom one), and describing a blockchain node connection info.
Such configuration should provide `protocol, hostname, port, pathPrefix` for a chain rpc node. Such key will be looked-up under the `platforms.<blockchain-name>` path in the config file.
(The only supported blockchain at the moment is "tezos").

You can manually add your own in a local config file, as well: refer to the 'Custom Configurations' section for additional information about _customizing the configuration files_.

NOTE: `Lorre` can run with some extra argument for a different behaviour, run it with `--help` for a list of accepted options

#### Database set-up

The application expects to read and write from a database compatible with [Typesafe Slick](http://slick.lightbend.com/). One can either run against a database installed on the local system or, as described below, against a containerized database instance. In any case, the instructions in the 'Custom configurations' section below should be used to provide the correct database parameters to Conseil.

##### Using a database instance

Cryptonomic uses Postgres for all its Conseil deployments. Once a Postgres database is set up, `sql/conseil.sql` can be used to set up the latest schema.

*For non-Postgres databases*: the schema file might have to be updated to reflect the idiosyncrasies of the particular SQL dialect being used.
Additionally you will probably need to generate the proper scala classes and update the codebase to use a different db-profile with Slick. To generate the code from a running up-to-date database you can run the sbt task from the project root:
```bash
env SBT_OPTS="-Dconfig.file={path to custom config file}" && sbt "genSchema"
```
Look at the task output to know where the file has been generated.

### Running in production

Build Conseil as a fat JAR file using the steps described in the previous sections.

Run this command using a script or as part of a service definition for Conseil:

```bash
java -Dconfig.file={path to custom config file} -cp {path to fat JAR file} tech.cryptonomic.conseil.Conseil
```

Then, run this command using a script or as part of a service definition for Lorre:

```bash
java -Dconfig.file={path to custom config file} -cp {path to fat JAR file} tech.cryptonomic.conseil.Lorre
```

See 'Custom Configurations' section for information about custom config files.

## How to use Conseil

After launching both Lorre and Conseil process you will be able to query the system for information on the blockchain and to execute operations.

An intro tutorial is available [here](https://github.com/Cryptonomic/Conseil/wiki/Tutorial:-Querying-for-Tezos-alphanet-data-using-the-v2-API)

## Local testing and development
The application expects to access a postgres database instance to run: you can run one locally, as already described, or you can use a [Docker](https://docs.docker.com/get-started/) image to run it within a container.

We won't be explaining how Docker works in detail, please refer to the official documentation.

### Using a Docker image for Postgres
A `docker-compose.yml` file is included in the root project directory that will launch a docker container with the database.
You need to customize the credentials in the YAML file or in your custom configuration [see the following section].

To run the database, from the project root
```bash
docker-compose up -d
```
This will launch the db container and setup the schema as described by the file under `sql/conseil.sql`

To stop the database
```bash
docker-compose stop
```
This will stop container but will not remove the data. You can remove container and the data with 
```bash
docker-compose rm
```
To stop the database and remove container
```bash
docker-compose down
```
This will stop and remove the container and the data stored.

### Unit Testing

The project includes an automated test suite that gets improved constantly.  To run the test you can use sbt from the root project directory
```bash
sbt test
```

There's currently no need to have a database running to run the test-suite. An embedded postgres db instance will automatically be downloaded and used during testing. Please make sure you have the libpq5 package installed, otherwise some of the unit tests using the embedded database may fail. For ubuntu the command should be
```bash
apt install libpq5
```

## Custom Configurations

Conseil uses [Typesafe Config](https://github.com/lightbend/config) for managing its configurations (including database access via [Slick](http://slick.lightbend.com/doc/3.2.0/database.html)). Please ensure you become familiar with both configuration systems before deploying Conseil. It is advisable to run with a custom config file which will automatically "inherit" defaults from the `src/main/resources/reference.conf` for production or manually include `src/main/resources/developer.conf` for local development.

In the latter case, parent configuration can be defined in the custom file using the `include` directive.

You might usually want to override definitions for the subsystem you're actually running (i.e. `lorre` or `conseil`), and the `platforms` you're connecting to.

### In Production
You can store the custom configuration anywhere and pass it to the runtime with the ` -Dconfig.file={path to custom config file}` command line option, as shown in the previous sections.

### During local development
Any custom file can be stored in the project directory itself, under \<path to Conseil\>/Conseil/src/main/resources
and referenced as a resource with the `-Dconfig.resource={custom-config-filename}` command line option, or as an external file, as explained before.

### The configuration entries
At a minimum the configuration file should include three key pieces of information:
- database connection
- blockchain node connection
- conseil server registered apikeys

Here is an example showing a default local development configuration defining the afore-mentioned config entries:

```coffee
include "developer"

#database connection
conseil.db: {
  dataSourceClass: "org.postgresql.ds.PGSimpleDataSource" #jdbc source
  properties: {
    databaseName: "conseil"
    user: "redacted"
    password: "redacted"
  }
  #please make sure you know what you're doing before changing these values
  numThreads: 10
  maxConnections: 10
}

#you might want to simply re-use the same database for local development
lorre.db: ${conseil.db}

#available blockchain platforms
platforms {
  tezos {
    #networks available on 'tezos'
    alphanet {
      node {
        hostname: "localhost",
        port: 8732
        pathPrefix: ""
      }
    }
  }
}
```

The imported `developer.conf` resource will in turn provide a list of api-keys in the form
```coffee
conseil.security.apiKeys.keys: [key1, key2, ...]
```

Such keys will be checked by the server when connecting via Http endpoints to Conseil, matching a required http-header with key `apiKey`.

The production configuration can override the default without resorting to inclusion of the dev conf.

---
For additional extra fine-grained configuration you can refer to [this appendix](docs/extra-custom-config.md)

## Publishing the artifacts
If you're a contributor and need to publish the artifacts on sonatype, you'll find instructions in the [publishing doc](docs/publishing.md)

## Contribution
If you want to contribute please read [practices we follow](CONTRIBUTION.md) in terms of organizing workflow with git.

## Technology Stack

This are the main libraries used throughout the project:

* [Cats](https://typelevel.org/cats)
  * fundamental support for functional programming development, providing all the basic blocks
* [Typelevel Cats-Effect](https://typelevel.org/cats-effect)
  * functional handling of "effects" based on type classes and the included `IO` type
* [Akka-Http](https://doc.akka.io/docs/akka-http/current)
  * used to provide client and server implementations for connecting to blockchain Rest APIs and to expose Conseil's own API
* [Fs2 (functional streaming for scala)](https://fs2.io)
  * used to collect concurrently multiple data elements and progressively process them inside
* [Circe](https://circe.github.io/circe) and [Jackson](https://github.com/FasterXML/jackson-module-scala)
  * we mostly employ Circe for the encoding/decoding of json, but some older parts still rely on Jackson
* [Endpoints](http://julienrf.github.io/endpoints)
  * used to describe our Rest Api and implement the OpenApi spec and Akka-http server from the same blueprint
* [Lightbend Slick](https://scala-slick.org)
  * we use this to define the database model, create and compose database queries, compose the asynchronous calls as needed
* [Pureconfig](https://pureconfig.github.io)
  * read configuration with early detection of expectations and type-safe modeling of the configured values
* [Scopt](https://github.com/scopt/scopt)
  * command line parsing of options/arguments
* [Scalatest](http://www.scalatest.org) and [Scalamock](http://scalamock.org)
  * unit testing and mock/stub/dummies