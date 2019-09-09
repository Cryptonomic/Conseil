#### Additional fine-grained configuration

You might want to override, _only after careful testing_, part of the following configuration entries

```coffee
# Runtime settings for Conseil server
conseil {
  hostname: "0.0.0.0"
  port: 1337
}

lorre {
  batchedFetches {
    # The following configs defines how many concurrent requests we'd like to run
    #   against a tezos node to speed up fetching of data

    #Used when getting accounts from tezos
    accountConcurrencyLevel: 5

    #Used when getting operation data for each new block just fetched
    blockOperationsConcurrencyLevel: 10

    #Used to paginate blocks read from tezos before each db storage
    blockPageSize: 500

  }
}

# Customization on the underlying actor system
akka {

  # custom host pool for akka-http client connections used for streaming request/responses
  # tune the configuration based on load-handling capability of tezos nodes
  # refer to host-connection-pool section in
  # https://doc.akka.io/docs/akka-http/current/configuration.html
  # for available properties and their meaning
  #
  # The current configuration is based on local benchmarking against zeronet
  # To improve or check it, look at the tech.cryptonomic.conseil.NodeStreamingBenchmark app
  # On the streaming http client pool we expect a max of:
  #   30 connections x
  #    7 requests/conn ~=
  #  210 ongoing requests at each moment
  # The pipelining on each connection might slow down for slow responses, but they should be rare
  tezos-streaming-client {
    max-connections: 30
    # The 2048 limit is thus overestimated by a factor of roughly 10x, to allow room for
    # reuse of the same pool from different threads at the same time, up to that factor
    max-open-requests: 2048
    # essentially keep connections alive across lorre's cycles
    idle-timeout: 10 minutes
    pipelining-limit: 7
    # give more room for async response in head-of-line blocking on the same connection or other slow responses
    response-entity-subscription-timeout: 5 seconds
  }

  # this is essentially available to enable composition of database operations
  # current configuration is based upon the default-blocking-io-dispatcher in akka
  tezos-dispatcher {
    type: "Dispatcher"
    executor: "thread-pool-executor"
    throughput: 1

    thread-pool-executor {
      fixed-pool-size: 16
    }
  }

  # akka-http-caching config described here:
  # https://doc.akka.io/docs/akka-http/current/common/caching.html#frequency-biased-lfu-cache
  http.caching.lfu-cache {
    max-capacity: 500
    initial-capacity: 100
    time-to-live: 5 minutes
    time-to-idle: 3 minutes
  }
}
```
