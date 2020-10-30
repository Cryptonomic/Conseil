#!/bin/bash

sudo apt-get install postgresql
sudo apt-get install postgres

pg_ctl start -l logfile

psql --command="CREATE DATABASE 'conseil';"

psql --filename="/sql/conseil.sql" conseil

CONFIG_PATH="conseil.conf"

touch CONFIG_PATH

echo "
include required(classpath(\"developer.conf\"))

platforms: [
  {
    name: \"tezos\"
    network: \"mainnet\"
    enabled: true
    node {
        #Replace the below entries with the address of your Tezos node
        protocol: \"https\",
        hostname: \"tezos-prod.cryptonomic-infra.tech\",
        port: 443
        path-prefix: ""
    }
  }
]

conseil {
 hostname: \"0.0.0.0\"
 port: 1337

 db {
  dataSourceClass = \"org.postgresql.ds.PGSimpleDataSource\"
  properties {
    # Replace the below lines with details of your database configured with the Conseil database schema
    user = \"postgres\"
    password = ""
    url = \"jdbc:postgresql://localhost:5432/conseil\"
  }
  numThreads = 20
  maxConnections = 20
}

lorre.db = \${conseil.db}
" > CONFIG_PATH

sbt clean assembly -J-Xss32m

java -Dconfig.file=conseil.conf -jar /tmp/conseil-lorre.jar tezos mainnet &

java -Dconfig.file=conseil.conf -jar /tmp/conseil-api.jar &

sleep 5m

sbt runApiTests