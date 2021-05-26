#!/bin/sh

##
# Script which is responsible for running specific module with or without custom configuration
##
if [ $1 = "conseil-api" ]; then
  if [ -z "${CONFIG_PATH}" ]; then
    echo "Bootstrapping Conseil API"
    java -Xmx$JVM_XMX -cp /root/conseil-api.jar tech.cryptonomic.conseil.api.Conseil
  else
    echo "Bootstrapping Conseil API with custom configuration from: $CONFIG_PATH..."
    java -Xmx$JVM_XMX -Dconfig.file=$CONFIG_PATH -cp /root/conseil-api.jar tech.cryptonomic.conseil.api.Conseil -d 10000
  fi
fi

if [ $1 = "conseil-lorre" ]; then
  if [ -z "${CONFIG_PATH}" ]; then
    echo "Bootstrapping Conseil Lorre"
    java -Xmx$JVM_XMX -cp /root/conseil-lorre.jar tech.cryptonomic.conseil.indexer.Lorre $LORRE_RUNNER_PLATFORM $LORRE_RUNNER_NETWORK
  else
    echo "Bootstrapping Conseil Lorre with custom configuration from: $CONFIG_PATH..."
    java -Xmx$JVM_XMX -Dconfig.file=$CONFIG_PATH -cp /root/conseil-lorre.jar tech.cryptonomic.conseil.indexer.Lorre $LORRE_RUNNER_PLATFORM $LORRE_RUNNER_NETWORK
  fi
fi
