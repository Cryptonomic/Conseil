
# Conseil Testing DSL

## Usage

```sh
source querycheck.fish
check -k 'hooman' -n 'ithacanet' -h 'http://127.0.0.1:1338' -N 'http://127.0.0.1:8732' -f queries/queriescheck.json -i 0
```

| Command Line Option | Description       |
|---------------------|-------------------|
| -k                  | Conseil Api Key   |
| -n                  | Tezos Network     |
| -h                  | Conseil Ip & Port |
| -N                  | Tezos Api & Port  |
| -f                  | JSON Query file using DSL |
| -i                  | Index of Query to run |


##### Example Query File 

```json
[
  {
    "title": "Top accounts",
    "variables": "",
    "path": "/v2/data/tezos/#S.network##/accounts",
    "query": {
      "fields": ["account_id", "balance"],
      "predicates": [],
      "orderBy": [
        {
          "field": "balance",
          "direction": "desc"
        }
      ],
      "aggregation": [],
      "limit": 50,
      "output": "json"
    },
    "check": [
      {
        "node": "/chains/main/blocks/#S.clevel##/context/contracts/#Q.account_id##/balance",
        "operation": {
	  "type": "compare",
          "relation": "1:N",
          "op": "eq",
          "field_1": "N.out",
          "field_2": "Q.balance",
          "error": "balance dont match #Q.account_id##, #Math.10 (Q.balance - N.out) / N.out  * 100##",
	  "ok" : " ... ok #Math.3 a = Q.balance , a / 77##"
        }
      }    ]
  }
]
```

