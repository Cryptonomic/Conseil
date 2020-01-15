#!/usr/bin/env bash

if [ "$1" != "local" ]; then
# required to push
    docker login
    [ $? -ne 0 ] && exit
fi

# needed to generate BuildInfo.scala
echo "Building Conseil to obtain version"
sbt clean compile

# gets version from BuildInfo.scala
DOCKER_TAG=`grep 'val version: String' ./target/scala-2.12/src_managed/main/sbt-buildinfo/BuildInfo.scala |  awk '{print $NF}' | sed -e 's/^"//' -e 's/"$//'`

# builds image
echo "Building Conseil image with tag $DOCKER_TAG"
docker build -t cryptonomictech/conseil:$DOCKER_TAG .

if [ "$1" != "local" ]; then
# pushes image to docker hub
    echo "Publishing Conseil image to Docker Hub with tag $DOCKER_TAG"
    docker push cryptonomictech/conseil:$DOCKER_TAG
fi
