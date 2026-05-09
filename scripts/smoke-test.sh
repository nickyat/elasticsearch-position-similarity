#!/usr/bin/env bash
set -euo pipefail

ES_IMAGE="${ES_IMAGE:-docker.elastic.co/elasticsearch/elasticsearch:9.4.0}"
PLUGIN_ZIP="${PLUGIN_ZIP:-target/releases/elasticsearch-position-similarity-9.4.0.zip}"
CONTAINER="${CONTAINER:-position-similarity-smoke}"
PORT="${PORT:-19201}"
BASE_URL="http://127.0.0.1:${PORT}"

log() {
    printf '%s\n' "$*"
}

fail() {
    printf 'Ошибка: %s\n' "$*" >&2
    exit 1
}

cleanup() {
    docker rm -f "${CONTAINER}" >/dev/null 2>&1 || true
}

[ -f "${PLUGIN_ZIP}" ] || fail "не найден ZIP плагина: ${PLUGIN_ZIP}. Сначала выполните mvn clean package -Dmaven.test.skip=true"

cleanup
trap cleanup EXIT

log "Запускаю Elasticsearch ${ES_IMAGE} с плагином ${PLUGIN_ZIP}"
docker run -d \
    --name "${CONTAINER}" \
    -p "127.0.0.1:${PORT}:9200" \
    -v "$(pwd)/target/releases:/plugins" \
    --entrypoint bash \
    -e discovery.type=single-node \
    -e xpack.security.enabled=false \
    -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
    "${ES_IMAGE}" \
    -c "bin/elasticsearch-plugin install --batch file:///plugins/$(basename "${PLUGIN_ZIP}") && /usr/local/bin/docker-entrypoint.sh eswrapper" >/dev/null

log "Жду готовности Elasticsearch..."
ready=false
for _ in $(seq 1 90); do
    if curl -fsS "${BASE_URL}" >/dev/null 2>&1; then
        ready=true
        break
    fi
    sleep 1
done
"${ready}" || {
    docker logs --tail 120 "${CONTAINER}" >&2 || true
    fail "Elasticsearch не поднялся за 90 секунд"
}

log "Создаю тестовый индекс с term_vector: with_positions"
curl -fsS -X DELETE "${BASE_URL}/position_smoke" >/dev/null 2>&1 || true
curl -fsS -H 'Content-Type: application/json' -X PUT "${BASE_URL}/position_smoke" -d \
    '{"mappings":{"properties":{"name":{"type":"text","term_vector":"with_positions"}}}}' >/dev/null

log "Индексирую документы"
curl -fsS -H 'Content-Type: application/json' -X POST "${BASE_URL}/position_smoke/_doc/1" -d '{"name":"телефон iphone"}' >/dev/null
curl -fsS -H 'Content-Type: application/json' -X POST "${BASE_URL}/position_smoke/_doc/2" -d '{"name":"чехол телефон"}' >/dev/null
curl -fsS -X POST "${BASE_URL}/position_smoke/_refresh" >/dev/null

log "Проверяю position_match"
response="$(curl -fsS -H 'Content-Type: application/json' -X POST "${BASE_URL}/position_smoke/_search" -d \
    '{"query":{"position_match":{"query":{"match":{"name":{"query":"телефон","operator":"and"}}}}}}')"

if [[ "${response}" == *'"_id":"1"'* ]] \
    && [[ "${response}" == *'"_score":1.0'* ]] \
    && [[ "${response}" == *'"_id":"2"'* ]] \
    && [[ "${response}" == *'"_score":0.8333333'* ]]; then
    log "Результат корректный: документ с термином в начале ранжируется выше"
else
    printf '%s\n' "${response}" >&2
    fail "position_match вернул неожиданный результат"
fi

log "Smoke test пройден"
