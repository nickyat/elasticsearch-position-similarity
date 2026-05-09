#!/usr/bin/env bash
set -euo pipefail

ES_VERSION="${ES_VERSION:-9.4.0}"
IMAGE="${IMAGE:-docker.elastic.co/elasticsearch/elasticsearch:${ES_VERSION}}"
CONTAINER="${CONTAINER:-es9-dev}"
HTTP_PORT="${HTTP_PORT:-9200}"
TRANSPORT_PORT="${TRANSPORT_PORT:-9300}"
DEBUG_PORT="${DEBUG_PORT:-5005}"

cd "$(dirname "$0")"
mkdir -p data plugins

docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true

docker run \
    --name "${CONTAINER}" \
    -p "${HTTP_PORT}:9200" \
    -p "${TRANSPORT_PORT}:9300" \
    -p "${DEBUG_PORT}:5005" \
    -e discovery.type=single-node \
    -v "$(pwd)/data:/usr/share/elasticsearch/data" \
    -v "$(pwd)/plugins:/plugins" \
    -e xpack.security.enabled=false \
    -e "ES_JAVA_OPTS=-Xms1g -Xmx1g -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" \
    "${IMAGE}"
