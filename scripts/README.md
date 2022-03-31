
# Conseil Testing DSL

## requirements
The interpreter is written for fish shell and will work on Mac OS and Linux.

- fish
- jq
- gsed
- curl
- pastel
- <mark>bc (*installed by default on most linux systems*)</mark>


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
          "error": "The balance does not  match for #Q.account_id##, percentage difference #Math.10 (Q.balance - N.out) / N.out  * 100##%",
          "ok": "The Balances returned by Conseil and the Tezos Node Match"
        }
      }
    ]
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
|query                            | Conseil Query for the Path Above                               |
|**check**                        | an Array of CheckObjects  *[CheckObject1 , CheckObject2, ...]*             |



###### [CheckObject] 


| Field                           | Description                     |
|---------------------------------|---------------------------------|
|node                            | RPC path of node to test against   ***(Allows Code Insert)*** |
|**operation**                   | The OperationObject defining our test                             |


###### OperationObject 


| Field                           | Description                     |
|---------------------------------|---------------------------------|
|type                            | Type of check to perform  **compare** (for comparison)                         |
|relation                        |**1:N** : for 1 query to conseil we query the node N times|
|op                               | Type of op to perform e.g. **eq** (for equality)                               |
|field_1                          | Definition of Field_1 in (***Code Block***)                               |
|field_2                          | Definition of Field_2 in (***Code Block***)                             |
|error                            | Message To Display if Test Fails     ***(Allows Code Insert)***                            |
|ok                               | Message To Display if Test succeeds    ***(Allows Code Insert)***                            |


## DSL

### Code Insert

Code block(s) written Conseil Testing DSL can be inserted in between a 
string of the relevant field mentioned above , by wrapping the Code between
<mark>**\#**</mark>  and  <mark>**\#\#**</mark>. Furthermore, multiple code Inserts can exist within the same
field separated by string content.

```console
#Code Block## 
```

> e.g.
> ```json
> {"path": "/v2/data/tezos/#S.network##/accounts"}
> ```
> 
> gets translated into 
> 
> ```json
> {"path": "/v2/data/tezos/ithacanet/accounts"}
> ```
>if the script was run with ***ithacanet*** as the network argument

### Code Block
A Code Block may start with any of the following Identifiers
 - S
 - Q
 - N
 - Math

#### S
Identifier **S** stands for system query. The following system identifiers are
available 

| System Identifier      | Description                           |
|------------------------|------------------------------------------|
|    S.network           |    The network command line argument's value  |
|    S.clevel            |    The block level Conseil's head is at the time of the query|


#### Q

Identifier **Q** stands for Conseil query. The following Conseil Query
identifiers are available 

| Query Identifier      | Description                           |
|------------------------|------------------------------------------|
|    Q.out               |    Output Conseil JSON response as is  |
|    Q.*                 |    here * represents the field to use from the returned Query Object|

> E.g. 
>
> Conseil response: 
>
> ```json
> [
>   {
>     "account_id": "tz1KhnTgwoRRALBX6vRHRnydDGSBFsWtcJxc",
>     "balance": 4998930345068662
>   },
>   {
>     "account_id": "tz1foxFdz2ieSj8P9YxKYBTEqYbgFXXEeiQY",
>     "balance": 4992349998899399
>   }
> ]
> ```
>
> `Q.out` will return the response as is 
> ```json
> [
>   {
>     "account_id": "tz1KhnTgwoRRALBX6vRHRnydDGSBFsWtcJxc",
>     "balance": 4998930345068662
>   },
>   {
>     "account_id": "tz1foxFdz2ieSj8P9YxKYBTEqYbgFXXEeiQY",
>     "balance": 4992349998899399
>   }
> ]
> ```
>
>
> Since we are using **1:N** as a relation , the language will assume 
> that the response from Conseil is an array with **N** entries which have 
> to be checked by making **N** calls to the node. And hence,  maps the
> check over the returned Conseil array array
>
>
> `Q.account_id ` would map the check over 
>	
>      tz1KhnTgwoRRALBX6vRHRnydDGSBFsWtcJxc
>      tz1foxFdz2ieSj8P9YxKYBTEqYbgFXXEeiQY

#### N

Identifier **N** stands for Tezos Node query. The following Tezos Node Query
identifiers are available 

| Node Identifier      | Description                           |
|------------------------|------------------------------------------|
|    N.out               |    Output Node's JSON response as is  |
|    N.*                 |    here * represents the field to use from the returned Node Object|

> E.g. 
>
> Node response: 
> 
>  ```json
>    {
>      "field": "value",
>      "child" : {
> 	      "field": "childValue"
>       }
>    }
>  ```
> 
> `N.out` will return the response as is 
> 
>  ```json
>    {
>      "field": "value",
>      "child" : {
> 	      "field": "childValue"
>       }
>    }
>  ```
> 
>
> Since we are using **1:N** as a relation , the language will assume 
> that the request has to be made to node for each Array member in
> Conseil's reponse. 
>
> `N.field` would produce
>	
>      value
> `N.child.field` would produce
>	
>      childValue

#### Math


|  Math Identifier      | Description                           |
|------------------------|------------------------------------------|
|    Math.precision   *MathBlock*       | Where precision is a natural number, `Math.18` means output in 18 decimal places   |
|    Math..           *MathBlock*       | Inherit the *MathBlock* State Space from the previous field |


##### MathBlock

The MathBlock contains a series of statements separated by <mark>,</mark>

     Statement1, Statement2, ... , StatementN


The MathBlock code is first scanned for any S,N or Q identifiers, and
the identifiers are replaced with the queried  values and finally fed to
bc [ *link to bc man page* ](https://man.archlinux.org/man/bc.1.en)
and all standard math operations and functions from bc are available. 

##### Defining and using variables


    Examples
    
    Math.2 a = 1 , b = 2 , a + b

    Math.18 a = N.out , b = Q.balance , a - b

    Math.3  a = 1 , a 


The last statement must end with a variable name or a math operation whose output
is to be used in a Code insert or field_1/field_2

###### Math..

Code Block starting with Math.. Has all the variable definitions
imported into its scope from the previous field containing a *MathBlock* . For example 

	"field_1" : "Math.2 a = 1, a",
	"field_2" : "Math.. a"

Here since, `field_1` is processed before `field_2` when using `Math..`
`field_2 ` inherits `a` or any other variables defined in `field_1`

### Order of processing of Code/Insert fields
 
 1. **.path** 
 2. **.check.node** 
 3. **.check.operation.field_1** 
 4. **.check.operation.field_2**
 5. **.check.operation.ok** *or* **.check.operation.error**

	

## TODO

This is only a ***proof of concept***. Naturally writing an interpreter in any
shell scripting language is not advisable , because of the performance
hit. 

A large part of the performance hit comes for the use of ***for loops*** to
process the code , moving these for loops from fish into jq might achieve a
drastic performance boost.

