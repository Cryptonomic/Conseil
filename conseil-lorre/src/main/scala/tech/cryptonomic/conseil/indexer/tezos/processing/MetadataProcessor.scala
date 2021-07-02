package tech.cryptonomic.conseil.indexer.tezos.processing

import akka.stream.ActorMaterializer
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Micheline
import tech.cryptonomic.conseil.indexer.config
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts.Tzip16
import tech.cryptonomic.conseil.indexer.tezos.{
  TezosGovernanceOperations,
  TezosIndexedDataOperations,
  TezosNamesOperations,
  TezosNodeOperator,
  Tzip16MetadataJsonDecoders,
  Tzip16MetadataOperator,
  TezosDatabaseOperations => TezosDb
}
import cats.instances.future._
import cats.syntax.applicative._
import cats.implicits._
import tech.cryptonomic.conseil.common.tezos
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MetadataProcessor(
    metadataOperator: Tzip16MetadataOperator,
    db: Database
)(implicit mat: ActorMaterializer)
    extends ConseilLogSupport {

  def processMetadata(implicit ec: ExecutionContext): Future[Option[Int]] = {
    logger.info("Starting processing metadata")
    db.run(TezosDb.getTzip16Contracts()).flatMap { contracts =>
      val nfts = contracts.filter(_.isNft).toList
      val nftMetadataAddresses = nfts.traverse { nft =>
        db.run(TezosDb.getInternalBigMapNfts(nft.metadataBigMapId)).map { bigMap =>
          bigMap.filter(_.valueMicheline.isDefined).map { param =>
            Tzip16
              .extractTzip16MetadataLocationFromParameters(Micheline(param.valueMicheline.get), None)
              .toList
              .map(res => (nft, param, res))
          }

        }
      }.map(_.flatten)
      val contractMetadataAddresses = contracts.map {
        case Tables.RegisteredTokensRow(_, _, _, accountId, _, _, _, _, "other", _, _, _) =>
          TezosDb.getOriginationByAccount(accountId).map { origList =>
            origList.toList.flatMap { orig =>
              orig.storageMicheline.toList.map(orig -> _)
            }.map {
              case (acc, storage) =>
                Tzip16
                  .extractTzip16MetadataLocationFromParameters(Micheline(storage), None, Some("ipfs"))
                  .map(acc -> _)

            }
          }
        case Tables.RegisteredTokensRow(_, _, _, accountId, _, _, _, _, "self", _, _, path) =>
          TezosDb.getOriginationByAccount(accountId).map { origList =>
            origList.toList.flatMap { orig =>
              orig.storageMicheline.toList.map(orig -> _)
            }.map {
              case (acc, storage) =>
                Tzip16
                  .extractTzip16MetadataLocationFromParameters(Micheline(storage), Some(path))
                  .map(acc -> _)
            }
          }
        case Tables.RegisteredTokensRow(_, _, _, accountId, _, _, _, _, "contract", _, _, path) =>
          TezosDb.getOriginationByAccount(accountId).map { origList =>
            origList.toList.flatMap { orig =>
              orig.storageMicheline.toList.map(orig -> _)
            }.map {
              case (acc, storage) =>
                Tzip16
                  .extractTzip16MetadataLocationFromParameters(Micheline(storage), Some(path))
                  .map(acc -> _)
            }
          }
        case Tables.RegisteredTokensRow(_, _, _, accountId, _, _, _, _, "web", _, _, _) =>
          TezosDb.getOriginationByAccount(accountId).map { origList =>
            origList.toList.flatMap { orig =>
              orig.storageMicheline.toList.map(orig -> _)
            }.map {
              case (acc, storage) =>
                Tzip16
                  .extractTzip16MetadataLocationFromParameters(Micheline(storage), None, Some("http"))
                  .map(acc -> _)
            }
          }
        case x =>
          logger.warn(s"Unknown metadata type $x")
          DBIO.successful(List.empty)
      }

      nftMetadataAddresses.flatMap { nfts =>
        Future
          .traverse(contractMetadataAddresses) { action =>
            db.run(action)
          }
          .map(metadata => nfts.flatten -> metadata.flatten.flatMap(_.toList))
      }.flatMap {
        case (nfts, metadata) =>
          val nftAddresses = nfts.foldLeft(
            (
              List.empty[(Tables.RegisteredTokensRow, Tables.BigMapContentsRow, String)],
              List.empty[(Tables.RegisteredTokensRow, Tables.BigMapContentsRow, String)]
            )
          ) {
            case ((ipfs, http), acc) if acc._3.startsWith("ipfs") => (acc :: ipfs, http)
            case ((ipfs, http), acc) if acc._3.startsWith("http") => (ipfs, acc :: http)
          }
          println(metadata)
          val metadataAddresses = metadata.foldLeft(
            (
              List.empty[(Tables.OperationsRow, String)],
              List.empty[(Tables.OperationsRow, String)]
            )
          ) {
            case ((ipfs, http), acc) if acc._2.startsWith("ipfs") => (acc :: ipfs, http)
            case ((ipfs, http), acc) if acc._2.startsWith("http") => (ipfs, acc :: http)
          }

          val (nftIpfs, nftHttp) = nftAddresses

          val (mdIpfs, mdHttp) = metadataAddresses

          import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
          import tech.cryptonomic.conseil.indexer.tezos.TezosDatabaseConversions._
          import tech.cryptonomic.conseil.common.sql.CustomProfileExtension.api._

          val nftsUpd = metadataOperator
            .getMetadataWithRegisteredTokensRow(nftIpfs.distinct ::: nftHttp.distinct)
            .map { nftFetchingResult =>
              nftFetchingResult
                .filter(_._2.isDefined)
                .map { case (x, y) => (x, y.get) }
                .map(_.convertTo[Tables.NftsRow])
            }
            .flatMap { nftRows =>
              db.run(Tables.Nfts.insertOrUpdateAll(nftRows))
            }
          val mdUpd = metadataOperator
            .getMetadataWithOperationsRow(mdIpfs.distinct ::: mdHttp.distinct)
            .map(
              _.filter(_._2.isDefined).map { case (x, y) => x -> y.get }
                .map(_.convertTo[Tables.MetadataRow])
            )
            .flatMap { mdRows =>
              db.run(Tables.Metadata.insertOrUpdateAll(mdRows.groupBy(x => x.name -> x.address).values.map(_.head)))
            }

          for {
            n <- nftsUpd
            m <- mdUpd
            _ = logger.info(s"Upserted NFTs ${n.getOrElse(0)} and Metadata ${m.getOrElse(0)}")
          } yield n |+| m
      }

    }

  }

}
