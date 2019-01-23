## Query interface

Query interface is using `POST` for passing the query

#### Example query
```
curl -vvv --request POST \
  --url '{{protocol}}://{{hostname}}:{{port}}/v2/data/{{platform}}/{{network}}/{{entity}}' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d @'query.file.json'
```

#### JSON syntax for the query
```
{
  "fields": ["field1", "field2" ...],   // array of fields for a given {{entity}}
  "predicates": [                       // array of predicates for filtering results
  	{ 
  	  "field": "field1",                // field on which predicate has to be applied
      "operation": "operation",         // all operations: in, between, like, lt, gt, eq, startsWith, endsWith, before, after
      "set": [
        2100, 2337                      // set of values against which the predicat will be matched, for example 'between' will use only 2 first fields to check if value is between first and second, 'in' will match if value is in this set, 'gt', 'lt', 'before', 'after', 'like', 'startsWith', 'endsWith' will check if this array contain only one element
      ],
      "inverse": false,                 // boolean parameter, in case when we want to negate the predicate, like 'not in this set' with operation 'in' and 'inverse' set to false - optional, defaults to false
      "precision": 2                    // optional parameter for comparison in decimal numbers, for example if we put in set '1.5' and in precision '2' we will match decimal values like '1.45'
  	}
  ],
  "orderBy": [                          // list of fields and directions to order by, starting with most significant
    {
      "field": "field_name",            // name of the field to order by
      "direction": "asc"                // direction to order by, asc or desc
    }
  ],
  "limit": 100
}
```


### In

#### example request

```
curl -vvv --request POST \
  --url 'http://localhost:1337/v2/data/tezos/alphanet/blocks' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["level", "timestamp", "protocol", "hash"],
"predicates": [
  {
    "field": "level",
    "operation": "in",
    "set": [
      2100, 2101, 2102, 2103, 2104
    ],
    "inverse": false
  }
],
"orderBy": [{"field":"level", "direction":"asc"}],
"limit" : 10
}'                                                                             
```

#### example response

```
[ {
  "level" : 2100,
  "timestamp" : 1533119682000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLmCEDk2RbV3H37SZEsy8Bk4QYpRdG1AKxGSZwvXeamn4huSVqb"
}, {
  "level" : 2101,
  "timestamp" : 1533119712000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BMM9m9KiBUDjZLESPUfBxYR25Sv7oLEBmZwav6aBXMVaaxr2Dev"
}, {
  "level" : 2102,
  "timestamp" : 1533119742000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BKsHuvtc3Fo4queWev4c7YGBPgZEoKnRfQjxMnhtuc6F3BqAfqg"
}, {
  "level" : 2103,
  "timestamp" : 1533119772000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLEANDkB7prpTfMyx1thwsLnRSz8sJQ7jvmsxVDvqfaid34dZCt"
}, {
  "level" : 2104,
  "timestamp" : 1533119802000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BKmUYCYVv4UyGJWbCsB8wShd4N2Q71XzHdJUYvNkdRtRsBiGUHZ"
} ]
```


### Between

#### Example request

```
curl -vvv --request POST \
  --url 'http://localhost:1337/v2/data/tezos/alphanet/blocks' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["level", "timestamp", "protocol", "hash"],
"predicates": [
  {
    "field": "level",
    "operation": "between",
    "set": [
      2100, 2103
    ],
    "inverse": false
  }
],
"orderBy": [{"field":"level", "direction":"asc"}],
"limit" : 5
}'                   
```                                   

#### Example response

```
[ {
  "level" : 2100,
  "timestamp" : 1533119682000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLmCEDk2RbV3H37SZEsy8Bk4QYpRdG1AKxGSZwvXeamn4huSVqb"
}, {
  "level" : 2101,
  "timestamp" : 1533119712000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BMM9m9KiBUDjZLESPUfBxYR25Sv7oLEBmZwav6aBXMVaaxr2Dev"
}, {
  "level" : 2102,
  "timestamp" : 1533119742000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BKsHuvtc3Fo4queWev4c7YGBPgZEoKnRfQjxMnhtuc6F3BqAfqg"
}, {
  "level" : 2103,
  "timestamp" : 1533119772000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLEANDkB7prpTfMyx1thwsLnRSz8sJQ7jvmsxVDvqfaid34dZCt"
} ]     
```


### Like

#### Example request

```
curl -vvv --request POST \
  --url 'http://localhost:1337/v2/data/tezos/alphanet/blocks' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["level", "timestamp", "protocol", "hash"],
"predicates": [
  {
    "field": "hash",
    "operation": "like",
    "set": [
      "LEAND"
    ],
    "inverse": false
  }
],
"orderBy": [{"field":"level", "direction":"asc"}],
"limit" : 5
}'                   
```   

#### Example response

```
[ {
  "level" : 2103,
  "timestamp" : 1533119772000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLEANDkB7prpTfMyx1thwsLnRSz8sJQ7jvmsxVDvqfaid34dZCt"
} ]
```


### Less than == before

#### Example request

```
curl -vvv --request POST \
  --url 'http://localhost:1337/v2/data/tezos/alphanet/blocks' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["level", "timestamp", "protocol", "hash"],
"predicates": [
  {
    "field": "level",
    "operation": "lt",
    "set": [
      2100
    ],
    "inverse": false
  }
],
"orderBy": [{"field":"level", "direction":"asc"}],
"limit" : 5
}'     
```

#### Example response

```
[ {
  "level" : 0,
  "timestamp" : 1533054159000,
  "protocol" : "PrihK96nBAFSxVL1GLJTVhu9YnzkMFiBeuJRPA8NwuZVZCE1L6i",
  "hash" : "BLockGenesisGenesisGenesisGenesisGenesis53242fHv7C1"
}, {
  "level" : 1,
  "timestamp" : 1533056272000,
  "protocol" : "Ps6mwMrF2ER2s51cp9yYpjDcuzQjsc2yAz8bQsRgdaRxw4Fk95H",
  "hash" : "BLVQGuKdm1CM7g8woGkcY9FjpdikUc32RUZQ8JiJABnVzxNXXA6"
}, {
  "level" : 2,
  "timestamp" : 1533056382000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BKnJHazJw6FtVRMhBMbacewV8MXNn71eKF9QNWFiHdVQK8Jji9W"
}, {
  "level" : 3,
  "timestamp" : 1533056452000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BMFC2dmYXW5i4Xk4wzha4UyEcffQDwWECHPeLdgja7TjGLtnxBL"
}, {
  "level" : 4,
  "timestamp" : 1533056482000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLEHQtLNzHFM7d1q5MPLNXXRp7b4Xo8ra3ZdbAGFwwMj6BTaoGw"
} ] 
```


### Greater than == after

#### Example request
```
curl -vvv --request POST \
  --url 'http://localhost:1337/v2/data/tezos/alphanet/blocks' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["level", "timestamp", "protocol", "hash"],
"predicates": [
  {
    "field": "level",
    "operation": "gt",
    "set": [
      2100
    ],
    "inverse": false
  }
],
"orderBy": [{"field":"level", "direction":"asc"}],
"limit" : 5
}'    
```

#### Example response

```
[ {
  "level" : 2101,
  "timestamp" : 1533119712000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BMM9m9KiBUDjZLESPUfBxYR25Sv7oLEBmZwav6aBXMVaaxr2Dev"
}, {
  "level" : 2102,
  "timestamp" : 1533119742000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BKsHuvtc3Fo4queWev4c7YGBPgZEoKnRfQjxMnhtuc6F3BqAfqg"
}, {
  "level" : 2103,
  "timestamp" : 1533119772000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLEANDkB7prpTfMyx1thwsLnRSz8sJQ7jvmsxVDvqfaid34dZCt"
}, {
  "level" : 2104,
  "timestamp" : 1533119802000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BKmUYCYVv4UyGJWbCsB8wShd4N2Q71XzHdJUYvNkdRtRsBiGUHZ"
}, {
  "level" : 2105,
  "timestamp" : 1533119832000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BMcU2eAsjjKQHDM5M1h1KZHsiqa6RVZ8XphHCJqZnWwAWMpJFuN"
} ]   
```



### Starts with

#### Example request

```
curl -vvv --request POST \
  --url 'http://localhost:1337/v2/data/tezos/alphanet/blocks' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["level", "timestamp", "protocol", "hash"],
"predicates": [
  {
    "field": "hash",
    "operation": "startsWith",
    "set": [
      "BLEAND"
    ],
    "inverse": false
  }
],
"orderBy": [{"field":"level", "direction":"asc"}],
"limit" : 5
}'                      
```

#### Example response

```
[ {
  "level" : 2103,
  "timestamp" : 1533119772000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLEANDkB7prpTfMyx1thwsLnRSz8sJQ7jvmsxVDvqfaid34dZCt"
} ]
```


### Ends with


#### Example request

```
curl -vvv --request POST \
  --url 'http://localhost:1337/v2/data/tezos/alphanet/blocks' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["level", "timestamp", "protocol", "hash"],
"predicates": [
  {
    "field": "hash",
    "operation": "endsWith",
    "set": [
      "qfaid34dZCt"
    ],
    "inverse": false
  }
],
"orderBy": [{"field":"level", "direction":"asc"}],
"limit" : 5
}'                      
```


#### Example response


```
[ {
  "level" : 2103,
  "timestamp" : 1533119772000,
  "protocol" : "PsYLVpVvgbLhAhoqAkMFUo6gudkJ9weNXhUYCiLDzcUpFpkk8Wt",
  "hash" : "BLEANDkB7prpTfMyx1thwsLnRSz8sJQ7jvmsxVDvqfaid34dZCt"
} ]  
```


### Invalid fields in query

#### Example request
```
curl --request POST \     
  --url 'http://localhost:1337/v2/data/tezos/alphanet/accounts' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["account_id", "spendableble", "counter", "balantines"],
"predicates": [
  {
    "field": "counterxd",
    "operation": "between",   
    "set": [
      2100, 2337                  
    ],
    "inverse": false
  },
  { 
    "field": "spendable",
    "operation": "eq",
    "set": [
      true
    ],
    "inverse": false
  }
```

#### Example response
```
400 - Bad Request
Errors: InvalidPredicateField(counterxd),InvalidQueryField(spendableble),InvalidQueryField(balantines)
```
