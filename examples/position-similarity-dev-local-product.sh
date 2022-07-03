#!/bin/sh

echo
curl --header "Content-Type:application/json" -s "localhost:9200/products/_search?pretty=true&size=50" -d '
{
  "explain": false,
  "query": {
    "position_match": {
      "query": {
        "match": {
          "nameSearch.position": {
            "operator": "and",
            "query": "лед бур"
          }
        }
      }
    }
  }
}
'
