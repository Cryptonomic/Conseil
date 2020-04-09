# Conseil

[![Build Status](https://travis-ci.org/Cryptonomic/Conseil.svg?branch=master)](https://travis-ci.org/Cryptonomic/Conseil)
[![Coverage Status](https://coveralls.io/repos/github/Cryptonomic/Conseil/badge.svg?branch=master)](https://coveralls.io/github/Cryptonomic/Conseil?branch=master)

Conseil is an indexer and API for blockchains, currently focused on the [Tezos](https://tezos.com/), which allows you to seamlessly run complex queries against blockchain data. It is written in the functional style in the [Scala](https://scala-lang.org/) programming language. This project forms an essential part of the [Cryptonomic stack](https://cryptonomic.tech/developers.html).

Conseil consists of two processes:
- Lorre, which can index the blockchain by downloading data from a blockchain node and storing it in a database.
- Conseil, which provides an API into the blockchain data indexed by Lorre.

[Arronax](https://arronax.io/) provides a convenient user interface for querying Conseil data.

Information on using, running, configuring and developing Conseil can be found on the [Conseil Wiki](https://github.com/Cryptonomic/Conseil/wiki).
