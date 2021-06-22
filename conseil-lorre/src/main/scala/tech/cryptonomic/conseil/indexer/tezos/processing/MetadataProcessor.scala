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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MetadataProcessor(
    metadataOperator: Tzip16MetadataOperator,
    db: Database
)(implicit mat: ActorMaterializer) {

  def processMetadata(implicit ec: ExecutionContext): Future[Option[Int]] = {
    println("STARTING PROCESSING METADATA")
    db.run(TezosDb.getTzip16Contracts()).flatMap { contracts =>
      val nfts = contracts.filter(_.isNft).toList
      val nftMetadataAddresses = nfts.traverse { nft =>
        db.run(TezosDb.getInternalTransactionsParameters(nft.accountId)).map { params =>
          //println(params)
          params.toList.flatMap(_.toList).flatMap { param =>
            Tzip16
              .extractTzip16MetadataLocationFromParameters(Micheline(param), Some(nft.metadataPath))
              .toList
              .map(nft -> _)
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
                  .extractTzip16MetadataLocationFromParameters(Micheline(storage), None)
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
                  .extractTzip16MetadataLocationFromParameters(Micheline(storage), None)
                  .map(acc -> _)
            }
          }
        case _ =>
          println("UNKNOWN!!!!!!!!")
          DBIO.from(Future.successful(List.empty))
      }

      nftMetadataAddresses.flatMap { xxx =>
        Future
          .traverse(contractMetadataAddresses) { yyy =>
            db.run(yyy)
          }
          .map(zzz => xxx -> zzz.flatten.flatMap(_.toList))
      }.flatMap {
        case (tuples, tuples1) =>
          val nftAddresses = tuples.foldLeft(
            (
              List.empty[(Tables.RegisteredTokensRow, String)],
              List.empty[(Tables.RegisteredTokensRow, String)],
              List.empty[(Tables.RegisteredTokensRow, String)]
            )
          ) {
            case ((ipfs, http, ts), (xx, yy)) if yy.startsWith("ipfs") => ((xx, yy) :: ipfs, http, ts)
            case ((ipfs, http, ts), (xx, yy)) if yy.startsWith("http") => (ipfs, (xx, yy) :: http, ts)
            case ((ipfs, http, ts), (xx, yy)) if yy.startsWith("tezos-storage") => (ipfs, http, (xx, yy) :: ts)
          }
          //println(s"XXXXXXXXXXXXX $nftAddresses")
          val metadataAddresses = tuples1.foldLeft(
            (
              List.empty[(Tables.OperationsRow, String)],
              List.empty[(Tables.OperationsRow, String)],
              List.empty[(Tables.OperationsRow, String)]
            )
          ) {
            case ((ipfs, http, ts), (xx, yy)) if yy.startsWith("ipfs") => ((xx, yy) :: ipfs, http, ts)
            case ((ipfs, http, ts), (xx, yy)) if yy.startsWith("http") => (ipfs, (xx, yy) :: http, ts)
            case ((ipfs, http, ts), (xx, yy)) if yy.startsWith("tezos-storage") => (ipfs, http, (xx, yy) :: ts)
          }
          println(s"YYYYYYYYYYYYY $metadataAddresses")

          val (nftIpfs, nftHttp, nftTs) = nftAddresses

          val (mdIpfs, mdHttp, mdTs) = metadataAddresses
          nftHttp.foreach { nft =>
            println(s"NFT HTTP LOCATION NOT FETCHED: $nft")
          }
          mdHttp.foreach { nft =>
            println(s"MD HTTP LOCATION NOT FETCHED: $nft")
          }
          nftTs.foreach { nft =>
            println(s"NFT TS LOCATION NOT FETCHED: $nft")
          }
          mdTs.foreach { nft =>
            println(s"MD TS LOCATION NOT FETCHED: $nft")
          }
          import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
          import tech.cryptonomic.conseil.indexer.tezos.TezosDatabaseConversions._
          import tech.cryptonomic.conseil.common.sql.CustomProfileExtension.api._
          println(nftIpfs.size)
          val nftsUpd = metadataOperator
            .getMetadataWithRegisteredTokensRow(nftIpfs.distinct)
            .map { zzz =>
              println("FETCHED NFTS")
              zzz
                .filter(_._2.isDefined)
                .map { case (x, y) => x -> y.get }
                .map(_.convertTo[Tables.NftsRow])
            }
            .flatMap { xxx =>
              db.run(Tables.Nfts ++= xxx)
            }
          val mdUpd = metadataOperator
            .getMetadataWithOperationsRow(mdIpfs.distinct)
            .map(
              _.filter(_._2.isDefined).map { case (x, y) => x -> y.get }
                .map(_.convertTo[Tables.MetadataRow])
            )
            .flatMap { xxx =>
              db.run(Tables.Metadata.insertOrUpdateAll(xxx.groupBy(x => x.name -> x.address).values.map(_.head)))
            }

          for {
            n <- nftsUpd
            m <- mdUpd
            _ = println(s"Upserted nfts $n metadata $m")
          } yield n |+| m
      }

    }

  }

}
