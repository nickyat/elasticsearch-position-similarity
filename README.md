# Elasticsearch position similarity plugin

Плагин добавляет query `position_match` для Elasticsearch 9.4.0. Он повышает score документов, где искомый term или фраза находятся ближе к началу поля.

Форк не использует payload для позиций. Позиции берутся из term vectors, поэтому поле должно быть настроено так:

```json
{
  "properties": {
    "name": {
      "type": "text",
      "term_vector": "with_positions"
    }
  }
}
```

## Сборка

```bash
mvn clean package -Dmaven.test.skip=true
```

Готовый архив:

```text
target/releases/elasticsearch-position-similarity-9.4.0.zip
```

Плагин собирается с `maven.compiler.release=17`. Elasticsearch 9.4.0 запускает его на bundled JDK.

## Установка

```bash
bin/elasticsearch-plugin install file:/path/to/elasticsearch-position-similarity-9.4.0.zip
```

После установки нужен рестарт Elasticsearch.

В Elasticsearch 9.4.0 при установке classic plugin может появляться предупреждение:

```text
WARNING: plugin has a policy file with no additional entitlements. Double check this is intentional.
```

Архив плагина не содержит `plugin-security.policy` или `entitlement-policy.yaml`; предупреждение не блокирует установку.

## Использование

```json
{
  "query": {
    "position_match": {
      "query": {
        "match": {
          "name": {
            "query": "телефон",
            "operator": "and"
          }
        }
      }
    }
  }
}
```

Например, документ `телефон iphone` получает score выше, чем `чехол телефон`, потому что term находится ближе к началу поля.

## Smoke Test

```bash
./scripts/smoke-test.sh
```

Скрипт:

- запускает временный контейнер Elasticsearch 9.4.0;
- устанавливает собранный ZIP плагина;
- создаёт индекс с `term_vector: with_positions`;
- индексирует два документа;
- проверяет, что `position_match` ранжирует документ с term в начале выше.

## Docker Для Отладки

```bash
./docker/dev.sh
```

Скрипт запускает локальный контейнер Elasticsearch 9.4.0 с debug-портом `5005`.

Переустановка плагина в уже запущенный debug-контейнер:

```bash
./scripts/reinstall-plugin-position-similarity
```
