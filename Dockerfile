FROM openjdk:8-stretch
RUN apt-get update && apt-get install -y apt-transport-https libpq5 && echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \
    apt-get update && \
    apt-get install -y sbt
RUN adduser --disabled-password --gecos '' builduser && su builduser
USER builduser
COPY --chown=builduser:builduser . /src
WORKDIR /src
RUN rm -rf test-postgres-path
RUN sbt clean assembly -J-Xss32m

FROM openjdk:13-alpine
RUN apk --no-cache add ca-certificates
WORKDIR /root/
COPY --from=0 /tmp/conseil.jar conseil.jar
ADD ./src/main/resources/metadata/tezos.alphanet.conf /root/tezos.alphanet.conf
ADD ./src/main/resources/metadata/tezos.mainnet.conf /root/tezos.mainnet.conf
ADD ./src/main/resources/metadata/tezos.babylonnet.conf /root/tezos.babylonnet.conf
ADD ./src/main/resources/metadata.conf /root/metadata.conf
ADD ./docker/template.conf /root/template.conf
ADD ./docker/entrypoint.sh /root/entrypoint.sh
RUN chmod +x /root/entrypoint.sh


ENTRYPOINT ["/root/entrypoint.sh"] 
