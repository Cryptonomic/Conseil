# Documentation for Query and Metadata interfaces

Description of endpoints with example usages. Probably all of those request will be added to Postman when query interface will be merged.

## Query interface

Query interface is using `POST` for passing the query

#### Example query
```
curl -vvv --request POST \
  --url '{{protocol}}://{{hostname}}:{{port}}/v2/data/{{platform}}/{{network}}/{{entity}}' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d @'{{query.file.json}}'
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

#### Example query:
```
curl -vvv --request POST \
  --url 'http://localhost:1337/v2/data/tezos/alphanet/accounts' \
  --header 'Content-Type: application/json' \
  --header 'apiKey: hooman' \
  -d '{
"fields": ["account_id", "spendable", "counter", "balance"],
"predicates": [
  {
    "field": "counter",
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
],
  "orderBy":[
    {
      "field":"account_id", 
      "direction": "asc"
    }
  ],
  "limit": 100
}'
```

#### example response:
```
[ {
  "account_id" : "tz1MHHw1fR6xNWeHdNi7Ss2dtQn9JEhoKQf6",
  "spendable" : true,
  "counter" : 2146,
  "balance" : 50000
}, {
  "account_id" : "tz1QKqxcFNNQ8pc39aY4iEdCjMqKXgVetHkt",
  "spendable" : true,
  "counter" : 2177,
  "balance" : 50000
}, {
  "account_id" : "tz1ZwkHmBrQwdFze1ZD49kBakeJ1JBQ4Rrt2",
  "spendable" : true,
  "counter" : 2230,
  "balance" : 13700669952
}, {
  "account_id" : "tz1fJvouvcpQy9v7Uck2orT9QE4Gkeee3N8R",
  "spendable" : true,
  "counter" : 2148,
  "balance" : 50000
} ]
```

### Metadata interface

Following endpoints provide possibility of getting the model of the db for usage in `Query interface`

### Platforms metadata

Request for listing the platforms - it depends on the config file:
```
curl -vvv --request GET \
  --url '{{protocol}}://{{hostname}}:{{port}}/v2/metadata/platforms' \
  --header 'apiKey: hooman'

```
#### example request:
```
curl -vvv --request GET \
  --url 'http://localhost:1337/v2/metadata/platforms' \
  --header 'apiKey: hooman'
```

#### example response:
```
[ {
  "name" : "tezos",
  "displayName" : "Tezos"
} ]
```

### Networks metadata
Request for getting all of the supported networks - at the moment one Conseil instance support one network at the time, values are being brought up from the config file:
```
curl -vvv --request GET \
  --url '{{protocol}}://{{hostname}}:{{port}}/v2/metadata/{{platform-name}}/networks' \
  --header 'apiKey: hooman'
```
#### example request:
```
curl -vvv --request GET \
  --url 'http://localhost:1337/v2/metadata/{{platform-name}}/networks' \
  --header 'apiKey: hooman'
```
#### example response:
```
[ {
  "name" : "prodnet",
  "displayName" : "Prodnet",
  "platform" : "tezos",
  "network" : "prodnet"
}, {
  "name" : "alphanet-staging",
  "displayName" : "Alphanet-staging",
  "platform" : "tezos",
  "network" : "alphanet-staging"
}, {
  "name" : "zeronet",
  "displayName" : "Zeronet",
  "platform" : "tezos",
  "network" : "zeronet"
}, {
  "name" : "alphanet",
  "displayName" : "Alphanet",
  "platform" : "tezos",
  "network" : "alphanet"
} ]
```

### Entities metadata

Provides information about which entities are available for querying

#### Request for getting all of the supported entities
```
curl -vvv --request GET \
  --url '{{protocol}}://{{hostname}}:{{port}}/v2/metadata/{{platform-name}}/{{network-name}}/entities' \
  --header 'apiKey: hooman'
```
#### example request:
```
curl -vvv --request GET \
  --url 'http://localhost:1337/v2/metadata/tezos/alphanet/entities' \
  --header 'apiKey: hooman'
```

#### example response:
```
[ {
  "name" : "blocks",
  "displayName" : "Blocks",
  "count" : 177499,
  "network" : "alphanet"
}, {
  "name" : "accounts",
  "displayName" : "Accounts",
  "count" : 2038,
  "network" : "alphanet"
}, {
  "name" : "operation_groups",
  "displayName" : "Operation groups",
  "count" : 516087,
  "network" : "alphanet"
}, {
  "name" : "operations",
  "displayName" : "Operations",
  "count" : 521237,
  "network" : "alphanet"
}, {
  "name" : "fees",
  "displayName" : "Fees",
  "count" : 0,
  "network" : "alphanet"
} ]
```

### Entity metadata


#### Request for getting all the attributes of given entity

Provides description of the attributes available in the given entity


```
curl -vvv --request GET \
  --url '{{protocol}}://{{hostname}}:{{port}}/v2/metadata/{{platform-name}}/{{network-name}}/{{entity-name}}/attributes' \
  --header 'apiKey: hooman'
```
#### example request:
```
curl -vvv --request GET \
  --url 'http://localhost:1337/v2/metadata/tezos/alphanet/accounts/attributes' \
  --header 'apiKey: hooman'
```

#### example response:
```
[ {
  "name" : "account_id",
  "displayName" : "Account id",
  "dataType" : "String",
  "cardinality" : 2038,
  "keyType" : "UniqueKey",
  "entity" : "accounts"
}, {
  "name" : "block_id",
  "displayName" : "Block id",
  "dataType" : "String",
  "cardinality" : 1,
  "keyType" : "NonKey",
  "entity" : "accounts"
}, {
  "name" : "manager",
  "displayName" : "Manager",
  "dataType" : "String",
  "cardinality" : 1266,
  "keyType" : "NonKey",
  "entity" : "accounts"
}, {
  "name" : "spendable",
  "displayName" : "Spendable",
  "dataType" : "Boolean",
  "cardinality" : 2,
  "keyType" : "NonKey",
  "entity" : "accounts"
}, {
  "name" : "delegate_setable",
  "displayName" : "Delegate setable",
  "dataType" : "Boolean",
  "cardinality" : 2,
  "keyType" : "NonKey",
  "entity" : "accounts"
}, {
  "name" : "delegate_value",
  "displayName" : "Delegate value",
  "dataType" : "String",
  "cardinality" : 86,
  "keyType" : "NonKey",
  "entity" : "accounts"
}, {
  "name" : "counter",
  "displayName" : "Counter",
  "dataType" : "Int",
  "cardinality" : 693,
  "keyType" : "NonKey",
  "entity" : "accounts"
}, {
  "name" : "script",
  "displayName" : "Script",
  "dataType" : "String",
  "cardinality" : 223,
  "keyType" : "NonKey",
  "entity" : "accounts"
}, {
  "name" : "balance",
  "displayName" : "Balance",
  "dataType" : "Decimal",
  "cardinality" : 1249,
  "keyType" : "NonKey",
  "entity" : "accounts"
}, {
  "name" : "block_level",
  "displayName" : "Block level",
  "dataType" : "Decimal",
  "cardinality" : 1,
  "keyType" : "NonKey",
  "entity" : "accounts"
} ]
```

### Attributes metadata

Provides all distinct values of chosen attribute - except for high cardinality fields

#### Request for getting all values of the given attribute:
```
curl -vvv --request GET \
  --url '{{protocol}}://{{hostname}}:{{port}}/v2/metadata/{{platform-name}}/{{network-name}}/{{entity-name}}/{{attribute-name}}' \
  --header 'apiKey: hooman'
```
#### example request:
```
curl -vvv --request GET \
  --url 'http://localhost:1337/v2/metadata/tezos/alphanet/accounts/delegate_value' \
  --header 'apiKey: hooman'
```

#### example response:
```
[ "tz1VkRsK1rYHVjYrJZqWNmDGQRMRmnNfCGUX", "tz1iCg6PHZkisiemWcwERZBop9tYEdVw8sJd", "tz1iZdhb5oaxoEtQg2Z73nzwCq1xSnZujZWJ", "tz1VdZ9Wm8rf4DgnuSud2bzxdqxK5K2Re7bm", "tz3WXYtyDUNL91qfiCJtVUX746QpNv5i5ve5", "tz3NdTPb3Ax2rVW2Kq9QEdzfYFkRwhrQRPhX", "tz1RTXyA27GVeXhBvA6qdutUzaRRofmbF6iB", "tz1bVAFnyovC7SEoANfitKqrQ9Ec53CXTBkM", "tz1Zg5RHm1G1b2AMtZNwuynX8sChrcy5K41f", null, "tz1Yi2y67e4fNAYp2ZmhSMxuSsCumfy3Fsfh", "tz1Y97VmFg7L2s9J2o9ecSTZDaD9w4Qr5EFf", "tz1NGYqqrHpE5GLb1dHieMwred7SzUHDidQz", "tz1LePMD9gxvbGMgQStoCvWAw4eCHfvqv5tH", "tz1WESA74Lm7UQfFS1Fm4qGimK6dtitU99i8", "tz1eGKPkRKc8dCa8gzs5NXtCNjpyQVsu6jap", "tz1g3Hb1GojyuTFCMvFW4i1mUG3cGkqvCYKY", "tz1fU53kw34jGCM66gVUhtxrEvnV7Ajac5bW", "tz1enAY9gv133dWioFe24jwcs1ps4mXFuFKA", "tz1PSoMfjLCVaDnBDNpDMGS7DfNRdeTFpyv2", "tz1fyYJwgV1ozj6RyjtU1hLTBeoqQvQmRjVv", "tz1LhS2WFCinpwUTdUb991ocL2D9Uk6FJGJK", "tz1cpE5LTaV3HkwbbcFiYxvjxyqGNtBaqe2s", "tz1UT7vYBX5eY8gYX587FNW3TrNBftRKjbLM", "tz1LTFTfpFXP6GyQmkh6Tnr11TrcpFhM2ZyA", "tz1bGcJxAvh75GAwRp9Gedur6z2hEci64a9x", "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ", "tz1btT98KZWK99WbQfvvnBSsveJp1YY69iqm", "tz1hjzSgyKDDm5FHZTRRd7xpxYSVwuZNjXTq", "tz1a3sMhA1wvYMQz8HGkNqSCoQ44X1yoT7w6", "tz1bhL4zwmLJvHJK5ejDDKdeatpqorvJdc2s", "tz1ZNFHPbkBEY5CWjtQ8GEzfUw6x8SXVC3zQ", "tz1LUFr9jQZqKJCoYBnobkWAXHiFPy6DQcUj", "tz1LzHyPBxieoLfoQT4YK7EGFCehQEj56X9i", "tz1PrWh3v16P99EBTPV92WgzUWqEescYN53J", "tz3e5mfRyZBcTcuerCrbxjtXW5bDyvA8wmPU", "tz1Y3JGezuAgo1YC2Tv8H4835gp1s3dpQYt6", "tz1M1nnzd8HQtt3VDdK2Su9yDmJisCa9N1aC", "tz1WcbghSgAKqjtJDBJrsQrzxPb6zwcMjLVk", "tz1X1y4yJKAih7Wwgxaun4QTEyzFb7sVQTWr", "tz2LgN1uN7AUwtdnGC4Bu8LGzHiTw5QQhCdQ", "tz1RzkGFKxqggJGhs18M3ejAkwnYNa3bNbFt", "tz1awU3Uo2F45cXKaY9X7KkucLyas76dpeCf", "tz1ZnDhjUTZ8LURHiCPWLP4ayYYYyV5Ddqyz", "tz1amfhHn47i5ZYVnUGsTodsZW6G52vqAThE", "tz1a4rq7TnGYr4cjN1FPGLh3voLUiknBYVfA", "tz1SJ74H8C3g9YdhpMdRqvsE8QeaQYESdfEq", "tz1SN46ZFMycvnPJFM37xxk24wK4wDT2KbZe", "tz1XV5grkdVLMC9x5cy8GSPLEuSKQeDi39D5", "tz1iLVCjraxVZm584zJzyfMWAWZCwt9QjfR3", "tz1XmX2QYNYtkTxtMYL7iaGgKS8vxaLRCPpE", "tz1d4fF7w2kXGNxUwuPUqzSWQG25Zs2mxJyV", "tz1QoVcn2FFC9CoFe59we38F8KNvv9yia6mC", "tz1NVUTRpHUoy9t4DEiBKCeyUaBjs8v8N8NB", "tz1SjzoafiHQ8yYBAQLzmggsew2VFMnEAtgD", "tz1MEVmsoxhTTukrbv45dZEvJC11ptDyrsBx", "tz1PSTFZBezQRMMV2Zk295L9pCu1qEF6k1o7", "tz1ZwkHmBrQwdFze1ZD49kBakeJ1JBQ4Rrt2", "tz1b7X44b7YmpfuDCJfgyYij4Hc4B6e7J5da", "tz3gN8NTLNLJg5KRsUU47NHNVHbdhcFXjjaB", "tz1aWXP237BLwNHJcCD4b3DutCevhqq2T1Z9", "tz1ZaMdaE9Qp3eU6acsQ8d4vvub4As8KeJJk", "tz2PVx7pTeTNaxWLzq2JAYf6NaAGg2um2LPJ", "tz1iAjuR4MMnf4bZo9DHJrsCBuLxMb9jdgAX", "tz1f8vTXY3jm3jKXnArjYjQ9AG7PVinDFvdF", "tz1funkyE3bAnfmaarN41iaa5fJC3W5ProGk", "tz1YCABRTa6H8PLKx2EtDWeCGPaKxUhNgv47", "tz1QJZDkPuajnSXHHkCgynxjsQBe7wxkvwgY", "tz1fhYDrVBxV7tEnDMAtgTFqGZcy4GNAybnN", "tz1b2rPGAQwvX4whvVNVZYMTBbk3Bwhu6vM3", "tz1c5zSbuq9qfNwHq2C4gjcRPWgFTXx1ZGX4", "tz1SazLSmCwRohSgfU85oqCLr5mvSsmPCFgH", "tz1XNrqWhTydoUB3qyefVZRs4HbmJahkmzVZ", "tz1URf65gvzo5onDViCAAC2PKBwYduAdH2Z5", "tz3bkFdXym6mHqJPkHHqbQMbesU5EViHEKmb", "tz1PygQHREmzb5aFQ4wKFjdbvTgS8ay7di85", "tz1h59SmGuwbjVispYe56fBh2LGDRHK6HxmL", "tz1eUsgK6aj752Fbxwk5sAoEFvSDnPjZ4qvk", "tz1foaS8v7zv3d3ANufKqjjqaVLbrnk4VGoQ", "tz1WKKjZnVHe4CtthLVPjGzEAFGb8BEFL3Uz", "tz1bxVgPCiveFHSand7zn22S8ZBtvNQbqgqU", "tz1MTh1BLcJHmdeEaEMr8Qy3SJgocM8MEu1N", "tz1TJhs1Sb81YxvQfMcT27q8x4a7MCCRq3BS", "tz1V7Vmvq2PbcKyL3sJZ8LYKDaFHRGPWCGqu", "tz1SfH1vxAt2TTZV7mpsN79uGas5LHhV8epq", "tz1W4QbdQsYDWVifj7kTLVEqpJVoLaW1qFZW", "tz1ZashkRLkUtJvHCijGSkaLppaRfiNEivuk" ]
```


### Attributes filtering

This endpoint gives possibility of filtering attributes by value

#### Request for filtering attributes by value:

```
curl -vvv --request GET \
  --url '{{protocol}}://{{hostname}}:{{port}}/v2/metadata/{{platform-name}}/{{network-name}}/{{entity-name}}/{{attribute-name}}/{{value-to-filter-with}}' \
  --header 'apiKey: hooman'
```

#### example request:
```
curl -vvv --request GET \
  --url 'http://localhost:1337/v2/metadata/tezos/alphanet/accounts/delegate_value/tz1Za' \
  --header 'apiKey: hooman'
```

#### example response:
```
[ "tz1ZaMdaE9Qp3eU6acsQ8d4vvub4As8KeJJk", "tz1ZashkRLkUtJvHCijGSkaLppaRfiNEivuk" ]
```
