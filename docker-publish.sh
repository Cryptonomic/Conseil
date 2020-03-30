#!/usr/bin/env bash

if [ "$1" != "local" ]; then
# required to push
    docker login
    [ $? -ne 0 ] && exit
fi

# builds image
echo "Building Conseil image"
docker build -t cryptonomictech/conseil .

if [ "$1" != "local" ]; then
# pushes image to docker hub
    echo "Publishing Conseil image to Docker Hub"
    docker push cryptonomictech/conseil
fi
