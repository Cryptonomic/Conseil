package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosDatabaseOperations._
import tech.cryptonomic.conseil.tezos.Tables._
import java.sql.Timestamp

class TezosDatabaseOperationsTest extends FlatSpec with MockFactory with Matchers with LazyLogging {

  "accountsToDatabaseRows" should "Turn account objects into rows ready to be inserted into a database" in {
    val block_hash: String = "BLEis22RCG4PZWvUZM78aUkgiQ2CwfKwutALe8SED2fBmYN3Qc2"
    val account_id : String = "TZ1igUcqJAVr8WQm1X8cpx31TxgcmmuDFSWo"
    val manager: String = "tz1NqkMf682968ukjGox8qaTYhjQTwKJ5E6B"
    val balance: scala.math.BigDecimal = 500.0
    val spendable: Boolean = false
    val delegateValue: Option[String] = None
    val delegate: AccountDelegate = AccountDelegate(false, delegateValue)
    val script: Option[Any] = None
    val counter: Int = 0
    val account: Account = Account(manager, balance, spendable, delegate, script, counter)
    val accounts: Map[String, Account] = Map(account_id ->  account)

    val accountsInfo : AccountsWithBlockHash = AccountsWithBlockHash(block_hash, accounts)

    val block_id : String = "BLEis22RCG4PZWvUZM78aUkgiQ2CwfKwutALe8SED2fBmYN3Qc2"
    val accountsRow: AccountsRow =
      AccountsRow(account_id, block_id, manager, spendable, false, delegateValue, balance, counter)
    val listAccountsRow: List[AccountsRow] = List(accountsRow)

    accountsToDatabaseRows(accountsInfo) should be (listAccountsRow)
  }

  "blockToDatabaseRow" should "Turn block objects into rows ready to be inserted into a database" in {
    val hash: String = "BM5KdTsHWqBftdHto4ipDS3DXBkkbZPKWUiRgut6yL34ZBNDATo"
    val net_id: String = "NetXj4yEEKnjaK8"
    val operations: Seq[Seq[BlockOperationMetadata]] = Seq(Seq())
    val protocol: String = "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK"
    val level: Int = 108175
    val proto: Int = 1
    val predecessor: String = "BLVRDgFJmYZNjqmCACrAgbsaYuguTEK5dNV9gcYMbHuemijDFTA"
    val timestamp: java.sql.Timestamp = Timestamp.valueOf("2018-03-19 13:06:20.0")
    val validation_pass: Int = 1
    val operations_hash: String = "LLoZsHM7p1HHBPvWV9aj5tYjJZVJEEwtsxUkVRtVWMhxXQ3brHEsp"
    val fitness: Seq[String] = Seq("00,00000000000e0730")
    val data: String =
      "0000cae1342613c0d3eac61fe48bb9cc4ffbf4d31bd97c23b90a91034138486796aaf01af53f218d222b4cbe15cd47" +
        "ef640b83255777e6feb49c4718e58e80096aed8edb3b453d980aec5c0784a30fd2ec1d6b3f285d577a85d5ab576c" +
        "92447b76f43dea943c6e35cb06"

    val metaData: BlockMetadata =
      BlockMetadata(hash, net_id, operations, protocol, level, proto, predecessor,
        timestamp, validation_pass, operations_hash, fitness, data)
    val operationGroups: List[OperationGroup] = List()
    val block: Block = Block(metaData, operationGroups)

    val blocksRow: BlocksRow =
      BlocksRow(net_id, protocol, level, proto, predecessor, validation_pass, operations_hash, data, hash,
        timestamp, fitness.head)
    blockToDatabaseRow(block) should be (blocksRow)
  }

  //Unfinished!, OperationGroups and Blocks have disjoint hash values, so not sure how this is supposed to work
  "operationGroupToDatabaseRow" should "Turn operationGroup objects into rows ready to be inserted into a database" in {
    val hash: String = "BM5KdTsHWqBftdHto4ipDS3DXBkkbZPKWUiRgut6yL34ZBNDATo"
    val net_id: String = "NetXj4yEEKnjaK8"
    val operations: Seq[Seq[BlockOperationMetadata]] = Seq(Seq())
    val protocol: String = "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK"
    val level: Int = 108175
    val proto: Int = 1
    val predecessor: String = "BLVRDgFJmYZNjqmCACrAgbsaYuguTEK5dNV9gcYMbHuemijDFTA"
    val timestamp: java.sql.Timestamp = Timestamp.valueOf("2018-03-19 13:06:20.0")
    val validation_pass: Int = 1
    val operations_hash: String = "LLoZsHM7p1HHBPvWV9aj5tYjJZVJEEwtsxUkVRtVWMhxXQ3brHEsp"
    val fitness: Seq[String] = Seq("00,00000000000e0730")
    val data: String =
      "0000cae1342613c0d3eac61fe48bb9cc4ffbf4d31bd97c23b90a91034138486796aaf01af53f218d222b4cbe15cd47" +
        "ef640b83255777e6feb49c4718e58e80096aed8edb3b453d980aec5c0784a30fd2ec1d6b3f285d577a85d5ab576c" +
        "92447b76f43dea943c6e35cb06"

    val metaData: BlockMetadata =
      BlockMetadata(hash, net_id, operations, protocol, level, proto, predecessor,
        timestamp, validation_pass, operations_hash, fitness, data)
    val operationGroups: List[OperationGroup] = List()
    val block: Block = Block(metaData, operationGroups)

    val operationGroupsRow: OperationGroupsRow = ???
      //OperationGroupsRow(hash, )
    operationGroupToDatabaseRow(block) should be (operationGroupsRow)
  }



}
