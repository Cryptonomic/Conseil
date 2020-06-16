package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataEndpoints

/** Trait containing endpoints definition */
trait BitcoinDataEndpoints extends ApiDataEndpoints with BitcoinDataJsonSchemas with BitcoinApiFilterFromQueryString {}
