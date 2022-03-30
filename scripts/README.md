
# Conseil Testing DSL

## requirements
The interpreter is written for fish shell and will work on Mac OS and Linux.

- <mark>fish</mark>
- <mark>jq</mark>
- <mark>gsed</mark>
- <mark>curl</mark>


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
| -N                  | Tezos node & Port |
| -f                  | Path to custom JSON file using the DSL |
| -i                  | Index of query to run |


##### Example JSON Query File 

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

The Query file is created in JSON format, and is an array
carrying the following format

```
[ 
  QueryObject1,
  QueryObject2, 
  ...         
  QueryObjectN
]

```

Each QueryObject in the array above needs to be defined as follows 


##### QueryObject


| Field                           | Description                     |
|---------------------------------|---------------------------------|
|title                            | Tile of the Query               |
|path                             | A Conseil POST path ***(Allows Code Insert)***            |
|query                            | Conseil Query for the Path                                |
|**check**                        | an Array of Check objects  *[check1 , check2, ...]*             |



###### **check** object 


| Field                           | Description                     |
|---------------------------------|---------------------------------|
|node                            | RPC path of node to test against   ***(Allows Code Insert)*** |
|**operation**                   | The object defining our test                             |


###### operation object 


| Field                           | Description                     |
|---------------------------------|---------------------------------|
|type                            | Type of check to perform  **compare** (for comparison)                         |
|relation                        |**1:N** : for 1 query to conseil we query the node N times|
|op                               | Type of op to perform e.g. **eq** (for equality)                               |
|field_1                          | Definition of Field_1 in **DSL**                               |
|field_2                          | Definition of Field_2 in **DSL**                             |
|error                            | Message To Display if Test Fails     ***(Allows Code Insert)***                            |
|ok                               | Message To Display if Test succeeds    ***(Allows Code Insert)***                            |


## DSL

### Code Insert

Code written Conseil Testing DSL can be inserted in between plain text
string of the fields mentioned above . simply by wrapping the Code
between **\#**  and  **\#\#**
```console
#code goes here## 
```



```json
{"path": "/v2/data/tezos/#S.network##/accounts"}
```

### Language

#### S

#### Q

#### N

#### Math

##### Precision

##### Defining and using variables


###### Math..


