#!/bin/bash
docker run  --name es8-dev -p 9200:9200 -p 9300:9300 -p 5005:5005 \
 -e "discovery.type=single-node" \
 -v `pwd`/data:/usr/share/elasticsearch/data \
 -v `pwd`/plugins:/plugins \
 -e "xpack.security.enabled=false" \
 -e ES_JAVA_OPTS="-Xms1g -Xmx1g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 " \
  docker-hub.adeo.pro/elasticsearch-rm:8.4.1