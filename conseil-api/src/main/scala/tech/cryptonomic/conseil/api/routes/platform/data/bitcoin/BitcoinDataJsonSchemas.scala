package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import tech.cryptonomic.conseil.common.bitcoin.Tables.BlocksRow
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataJsonSchemas

/** Trait containing Data endpoints JSON schemas */
trait BitcoinDataJsonSchemas extends ApiDataJsonSchemas {

  /** Blocks row schema */
  implicit lazy val blocksRowSchema: JsonSchema[BlocksRow] =
    genericJsonSchema[BlocksRow]

}
