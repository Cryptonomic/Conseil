
# Conseil Testing DSL

## Usage

```sh
source querycheck.fish
check -k 'hooman' -n 'ithacanet' -h 'http://127.0.0.1:1338' -N 'http://127.0.0.1:8732' -f queries/queriescheck.json -i 0
```

| Option              | Description       |
|---------------------|-------------------|
| -k                  | Conseil API Key   |
| -n                  | Tezos Network     |
| -h                  | Conseil IP & Port |
| -N                  | Tezos Api & Port  |
| -f                  | Path to custom JSON file using the DSL |
| -i                  | Index of Query to run |


#### Example Query File 

```json
[
  {
    "title": "Top accounts",
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


#### Query File Format

The Query file is created in JSON format, and has the following format

```
[ QueryObeject1 , QueryObject2, .... QueryObjectN ]
```

Each QueryObject in the array needs to be defined as follows 



| Field                           | Description                     |
|---------------------------------|---------------------------------|
|title                            |                                 |
|path                             |                                 |
|query                            |                                 |
|**check**                            |                                 |


###### check :


| Field                           | Description                     |
|---------------------------------|---------------------------------|
|node                            |                                 |
|**operation**                       |                                 |


###### operation :


| Field                           | Description                     |
|---------------------------------|---------------------------------|
|type                            |                                 |
|relation                        |                                 |
|op                               |                                 |
|field_1                          |                                 |
|field_2                          |                                 |
|error                            |                                 |
|ok                               |                                 |

