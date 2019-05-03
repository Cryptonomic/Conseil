# Conseil

A blockchain indexer for building decentralized applications, currently focused on Tezos.

Conseil is a fundamental part of the [Nautilus](https://github.com/Cryptonomic/Nautilus) infrastructure for high-performance chain analytics. Check out [ConseilJS](https://github.com/Cryptonomic/ConseilJS) for a Typescript wrapper with a Tezos node interface.

## Running Conseil
 
### Starting and Stopping

There are two processes to consider, lorre and conseil.  Lorre retrieves blocks from the tezos node and places it in the db, conseil provides the front end so that the end user and applications can interact with the blockchain.  

To run lorre, an example command is:

```java -Xms512m -Xmx14g -Dconfig.file=conseil.conf -cp conseil.jar tech.cryptonomic.conseil.Lorre alphanet```

In this command, the ```-Xms512m``` and ```-Xmx14g``` can be changed based on the amount of memory available on the system, the ```-Dconfig.file=conseil.conf``` can be changed to point to any user created config file contains the credentials for the respective db and tezos node, the file can also contain any overrides for settings provided by the ```application.conf``` file from the repo.  Lastly, ```alphanet``` is the network being run by the tezos node, it can and should be changed if running mainnet or zeronet.

To run conseil, an example command is:

```java -Xms512m -Xmx2g -Dconfig.file=conseil.conf -cp conseil.jar tech.cryptonomic.conseil.Conseil```

The characteristics of the attributes for lorre apply here as well, with the omission of the network name.  As this is the front end, it does not require as much of a memory commitment as Lorre given that conseil's main function is to interact with the chain on an ad-hoc basis.  

Both of these can be placed in a bash file and also can be run as services.  Configuring this as a service is beyong the scope of this README.  

###Logging

Both conseil and lorre write to syslog, the logs are currently verbose enought to determine the point of synchronization between the db, the blockchain, and the status of lorre/conseil.  If any issues arise, please first check the log to see if the services are running and if the chain and db are synced as this would be the starting point of all troubleshooting enqueries. 


### Configuration 

### Datasource configuration

In addition to the metadata derived from the schema, it's possible to extend or override it. In the `/src/main/resources/reference.conf` file under the `metadata-overrides` section there is a structure in the format: platform/network/entity/attribute that allows this. For example table columns used for internal purposes like foreign key constraints can be hidden from view with `visible: false`. This applies to whole entities as well. It's possible to override the `displayName` property at any level as well. Preferred, or default `dataformat` can also be added.

## REST API Overview &amp; Examples

If you're already running a Conseil instance, there is an OpenAPI route at `<conseil-host>/docs`.

### Metadata Discovery

Conseil provides a dynamic query API. This means that newly exposed datasets can be automatically served through the interface. The metadata routes have a prefix of `/v2/metadata/`. All results are in JSON format.

#### Platforms

To get a list of platforms, make the following call.

```bash
curl --request GET --header 'apiKey: hooman' --url '<conseil-host>/v2/metadata/platforms'
```

The result may look like the following.

```json
[{
	"name": "tezos",
	"displayName": "Tezos"
}]
```

#### Networks

To get a list of networks, take one of the platform `name` attributes from the call above to construct the following URL.

```bash
curl --request GET --header 'apiKey: hooman' --url '<conseil-host>/v2/metadata/tezos/networks'
```

On a development server the result might be this:

```json
[{
	"name": "zeronet",
	"displayName": "Zeronet",
	"platform": "tezos",
	"network": "zeronet"
}, {
	"name": "alphanet",
	"displayName": "Alphanet",
	"platform": "tezos",
	"network": "alphanet"
}]
```

#### Entities

Taking again the `name` property from one of the network results, we can list the entities available for that network.

```bash
curl --request GET --header 'apiKey: hooman' --url '<conseil-host>/v2/metadata/tezos/alphanet/entities'
```

WIth the following sample result.

```json
[{
	"name": "accounts",
	"displayName": "Accounts",
	"count": 19587
}, {
	"name": "accounts_checkpoint",
	"displayName": "Accounts checkpoint",
	"count": 5
}, {
	"name": "bakers",
	"displayName": "Bakers",
	"count": 9232185
}, {
	"name": "balance_updates",
	"displayName": "Balance updates",
	"count": 8848857
}, {
	"name": "ballots",
	"displayName": "Ballots",
	"count": 0
}, {
	"name": "blocks",
	"displayName": "Blocks",
	"count": 346319
}, {
	"name": "fees",
	"displayName": "Fees",
	"count": 1680
}, {
	"name": "operation_groups",
	"displayName": "Operation groups",
	"count": 2590969
}, {
	"name": "operations",
	"displayName": "Operations",
	"count": 2619443
}, {
	"name": "proposals",
	"displayName": "Proposals",
	"count": 0
}]
```

The `count` property above contains the number of records for a particular entity.

Keep in mind that not all of the entities provided in that list map directly to on-chain entities on Tezos alphanet. Conseil may provide custom pre-processed data for convenience. In this example the `fees` is one such entity that contains moving averages for operation fees in Tezos.

#### Attributes

Using the results from the `entities` call, we can get details of their composition.

```bash
curl --request GET --header 'apiKey: hooman' --url '<conseil-host>/v2/metadata/tezos/alphanet/accounts/attributes'
```

```json
[{
	"name": "account_id",
	"displayName": "Account id",
	"dataType": "String",
	"cardinality": 19587,
	"keyType": "UniqueKey",
	"entity": "accounts"
}, {
	"name": "block_id",
	"displayName": "Block id",
	"dataType": "String",
	"cardinality": 4614,
	"keyType": "NonKey",
	"entity": "accounts"
}, {
	"name": "manager",
	"displayName": "Manager",
	"dataType": "String",
	"cardinality": 15572,
	"keyType": "UniqueKey",
	"entity": "accounts"
}, {
	"name": "spendable",
	"displayName": "Spendable",
	"dataType": "Boolean",
	"cardinality": 2,
	"keyType": "NonKey",
	"entity": "accounts"
}, {
	"name": "delegate_setable",
	"displayName": "Delegate setable",
	"dataType": "Boolean",
	"cardinality": 2,
	"keyType": "NonKey",
	"entity": "accounts"
}, {
	"name": "delegate_value",
	"displayName": "Delegate value",
	"dataType": "String",
	"cardinality": 143,
	"keyType": "NonKey",
	"entity": "accounts"
}, {
	"name": "counter",
	"displayName": "Counter",
	"dataType": "Int",
	"keyType": "NonKey",
	"entity": "accounts"
}, {
	"name": "script",
	"displayName": "Script",
	"dataType": "String",
	"cardinality": 1317,
	"keyType": "NonKey",
	"entity": "accounts"
}, {
	"name": "balance",
	"displayName": "Balance",
	"dataType": "Decimal",
	"keyType": "NonKey",
	"entity": "accounts"
}, {
	"name": "block_level",
	"displayName": "Block level",
	"dataType": "Decimal",
	"keyType": "UniqueKey",
	"entity": "accounts"
}]
```

Using the information above it is possible to make intelligent decisions about how to construct user interfaces. For example, [Arronax](https://github.com/Cryptonomic/Arronax) creates drop-down lists for value selection for low-cardinality properties like `accounts.spendable`. Furthermore the `datatype` field allows display of the appropriate editor be it numeric or date entry. You'll note also that certain properties do no contain `cardinality`. Those are considered high cardinality fields for which that metadata is not collected.

#### Attribute Values

Finally for low-cardinality fields it is possible to get a list of distinct values to populate the drop-down lists mentioned above for example.

```bash
curl --request GET --header 'apiKey: <API key>' --url '<conseil-host>/v2/metadata/tezos/alphanet/operations/kind'
```

These are the operation types this particular Conseil instance has seen since start.

```json
["seed_nonce_revelation", "delegation", "transaction", "activate_account", "origination", "reveal", "double_endorsement_evidence", "double_baking_evidence", "endorsement"]
```

#### Attribute Values with Prefix

It is possible to get values for high-cardinality properties with a prefix. In this example we get all `accounts.manager` values starting with "KT1".

```bash
curl --request GET --header 'apiKey: hooman' --url 'https://conseil-dev.cryptonomic-infra.tech:443/v2/metadata/tezos/alphanet/accounts/manager/KT1'
```

```json
...
```

#### Extended metadata

As mentioned in the [Datasource configuration](#datasource-configuration) section, it's possible to provide additional details for the items Conseil manages. 

### Tezos Chain Data Query

Data requests in Conseil v2 API are sent via `POST` command and have a prefix of `/v2/data/<platform>/<network>/<entity>`. The items in curly braces match up to the `name` property of the related metadata results. There additional header to be set in the request: `Content-Type: application/json`.

The most basic query has the following JSON structure.

```json
{
	"fields": [],
	"predicates": [],
	"limit": 5
}
```

This will return 5 items without any filtering for some entity. Note that the query does not specify which entity is being requested. If this is sent to `/v2/data/tezos/alphanet/blocks`, it will return five rows with all the fields available in the blocks entity.

```bash
curl -d '{ "fields": [], "predicates": [], "limit": 5 }' -H 'Content-Type: application/json' -H 'apiKey: <API key>' -X POST '<conseil-host>/v2/data/tezos/alphanet/blocks/'
```

The query grammar allows for some fine-grained data extraction.

#### `fields`

It is possible to narrow down the fields returned in the result set by listing just the ones that are necessary. The `fields` property of the query is an array of `string`, these values are the `name`s from the `/v2/metadata/<platform>/<network>/<entity>/attributes/` metadata response.

#### `predicates`

`predicates` is an array containing objects. The inner object as several properties:
- `field` – attribute name from the metadata response.
- `operation` – one of: in, between, like, lt, gt, eq, startsWith, endsWith, before, after.
- `set` – an array of values to compare against. `in` requires two or more elements, `between` must have exactly two, the rest of the operations require a single element.
- `inverse` – boolean, setting it to `true` applied a `NOT` to the operator
- `precision` – for numeric field matches this specifies the number of decimal places to round to.

#### `limit`

Specifies the maximum number of records to return.

#### `orderBy`

Sort condition, multiple may be supplied for a single query. Inner obejct(s) will contain `field` and `direction` properties. The former is `name` from the `/v2/metadata/<platform>/<network>/<entity>/attributes/` metadata response, the latter is one of 'asc', 'desc'.

#### `aggregation`

It is possible to apply an aggregation function to single field of the result set. The aggregation object contains the following properties:

- `field` – `name` from the `/v2/metadata/<platform>/<network>/<entity>/attributes/` metadata response.
- `function` – one of: sum, count, max, min, avg.
- `predicate` – same definition as the predicate object described above. This gets translated into a `HAVING` condition in the underlying SQL.

#### `output`

Default result set format is JSON, it is possible however to output csv instead for exporting larger datasets that will then be imported into other tools.

`'output': 'csv|json'`
