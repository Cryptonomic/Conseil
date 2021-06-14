package tech.cryptonomic.conseil.indexer.tezos.processing

import akka.stream.ActorMaterializer
import slick.jdbc.JdbcBackend.Database
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Micheline
import tech.cryptonomic.conseil.indexer.config
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts.Tzip16
import tech.cryptonomic.conseil.indexer.tezos.{TezosIndexedDataOperations, TezosNodeOperator, Tzip16MetadataOperator}
import tech.cryptonomic.conseil.indexer.tezos.{TezosGovernanceOperations, TezosNamesOperations, TezosNodeOperator, Tzip16MetadataOperator, TezosDatabaseOperations => TezosDb}

import scala.concurrent.ExecutionContext
import scala.util.Try

class MetadataProcessor(
  metadataOperator: Tzip16MetadataOperator,
  indexedData: TezosIndexedDataOperations,
  db: Database,
  configuration: config.BakingAndEndorsingRights
)(implicit mat: ActorMaterializer) {

  def processMetadata(implicit ec: ExecutionContext) = {

    db.run(TezosDb.getTzip16Contracts()).map { contracts =>
      val nfts = contracts.filter(_.isNft)
      nfts.map { nft =>
        val sth = db.run(TezosDb.getNftTokensForAccount(nft.accountId)).map { nfts =>
          Try(nfts.minBy(_.lastUpdated.map(_.toInstant.toEpochMilli).getOrElse(0L))).toOption.getOrElse(0L)
        }
        Tzip16
          .extractTzip16MetadataLocationFromParameters(Micheline(storage), None)
      }
      contracts.map {
        case Tables.RegisteredTokensRow(_, _, _, accountId, _, _, _, _, "other", _, _, _) =>
          TezosDb.getAccountById(accountId).map { accList =>
            accList.flatMap { acc =>
              acc.storage.toList
            }.map { storage =>
              Tzip16
                .extractTzip16MetadataLocationFromParameters(Micheline(storage), None)
            }
          }
        case Tables.RegisteredTokensRow(_, _, _, accountId, _, _, _, _, "self", _, _, path) =>
          TezosDb.getAccountById(accountId).map { accList =>
            accList.flatMap { acc =>
              acc.storage.toList
            }.map { storage =>
              Tzip16
                .extractTzip16MetadataLocationFromParameters(Micheline(storage), Some(path))
            }
          }
        case Tables.RegisteredTokensRow(_, _, _, accountId, _, _, _, _, "contract", _, _, path) =>
          TezosDb.getAccountById(accountId).map { accList =>
            accList.flatMap { acc =>
              acc.storage.toList
            }.map { storage =>
              Tzip16
                .extractTzip16MetadataLocationFromParameters(Micheline(storage), Some(path))
            }
          }
        case Tables.RegisteredTokensRow(_, _, _, accountId, _, _, _, _, "web", _, _, _) =>
          TezosDb.getAccountById(accountId).map { accList =>
            accList.flatMap { acc =>
              acc.storage.toList
            }.map { storage =>
              Tzip16
                .extractTzip16MetadataLocationFromParameters(Micheline(storage), None)
            }
          }
      }

    }


  }

}
