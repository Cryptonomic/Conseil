package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp
import java.time.{LocalDate, ZoneOffset}

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.H2Profile.api._
import tech.cryptonomic.conseil.tezos.Tables.{BlocksRow, OperationsRow, OperationGroupsRow, AccountsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees

import scala.util.Random

/* use this to make random generation implicit but deterministic */
case class RandomSeed(seed: Long) extends AnyVal with Product with Serializable {
  def +(extra: Long): RandomSeed = RandomSeed(seed + extra)
}

class TezosDatabaseOperationsTest
  extends WordSpec
    with MockFactory
    with InMemoryDatabase
    with Matchers
    with ScalaFutures
    with OptionValues
    with LazyLogging {

  "The database api" should {

    //needed for most tezos-db operations
    import scala.concurrent.ExecutionContext.Implicits.global

    val sut = TezosDatabaseOperations

    "write fees" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val expectedCount = 5
      val generatedFees = generateFees(expectedCount, testReferenceTime)

      val writeAndGetRows = for {
        written <- sut.writeFees(generatedFees)
        rows <- Tables.Fees.result
      } yield (written, rows)

      val (stored, dbFees) = dbHandler.run(writeAndGetRows.transactionally).futureValue

      stored.value shouldEqual expectedCount

      dbFees should have size expectedCount

      import org.scalatest.Inspectors._

      forAll(dbFees zip generatedFees) { case (row, fee) =>
        row.low shouldEqual fee.low
        row.medium shouldEqual fee.medium
        row.high shouldEqual fee.high
        row.timestamp shouldEqual fee.timestamp
        row.kind shouldEqual fee.kind
      }
    }

    "write blocks" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val basicBlocks = generateBlocks(5, testReferenceTime)
      val generatedBlocks = basicBlocks.zipWithIndex map {
        case (block, idx) =>
          //need to use different seeds to generate unique hashes for groups
          val group = generateOperationGroup(block, operations = 1)(randomSeed + idx)
          block.copy(operationGroups = List(group))
      }

      whenReady(dbHandler.run(sut.writeBlocks(generatedBlocks))) {
        _ =>
          //read and check what's on db
          val dbBlocks = dbHandler.run(Tables.Blocks.result).futureValue

          dbBlocks should have size (generatedBlocks.size)

          import org.scalatest.Inspectors._

          forAll(dbBlocks zip generatedBlocks) {
            case (row, block) =>
              row.level shouldEqual block.metadata.header.level
              row.proto shouldEqual block.metadata.header.proto
              row.predecessor shouldEqual block.metadata.header.predecessor.value
              row.timestamp shouldEqual block.metadata.header.timestamp
              row.validationPass shouldEqual block.metadata.header.validationPass
              row.fitness shouldEqual block.metadata.header.fitness.mkString(",")
              row.context.value shouldEqual block.metadata.header.context
              row.signature shouldEqual block.metadata.header.signature
              row.protocol shouldEqual block.metadata.protocol
              row.chainId shouldEqual block.metadata.chain_id
              row.hash shouldEqual block.metadata.hash.value
              row.operationsHash shouldEqual block.metadata.header.operations_hash
          }

          val dbBlocksAndGroups =
            dbHandler.run {
              val query = for {
                g <- Tables.OperationGroups
                b <- g.blocksFk
              } yield (b, g)
              query.result
            }.futureValue

          dbBlocksAndGroups should have size (generatedBlocks.size)

          forAll(dbBlocksAndGroups) {
            case (blockRow, groupRow) =>
              val blockForGroup = generatedBlocks.find(_.metadata.hash.value == blockRow.hash).value
              val group = blockForGroup.operationGroups.head
              groupRow.hash shouldEqual group.hash.value
              groupRow.blockId shouldEqual blockForGroup.metadata.hash.value
              groupRow.chainId shouldEqual group.chain_id
              groupRow.branch shouldEqual group.branch
              groupRow.signature shouldEqual group.signature
          }

          val dbOperations =
           dbHandler.run {
             val query = for {
              o <- Tables.Operations
              g <- o.operationGroupsFk
            } yield (g, o)
            query.result
           }.futureValue

          val generatedGroups = generatedBlocks.map(_.operationGroups.head)

          dbOperations should have size (generatedGroups.map(_.contents.map(_.size).getOrElse(0)).sum)

          forAll(dbOperations) {
            case (groupRow, opRow) =>
              val operationBlock = generatedBlocks.find(_.operationGroups.head.hash.value == groupRow.hash).value
              val operationGroup = generatedGroups.find(_.hash.value == groupRow.hash).value
              val operation = operationGroup.contents.value.head
              opRow.kind shouldEqual operation.kind
              opRow.source shouldEqual operation.source
              opRow.fee shouldEqual operation.fee
              opRow.storageLimit shouldEqual operation.storageLimit
              opRow.gasLimit shouldEqual operation.gasLimit
              opRow.amount shouldEqual operation.amount
              opRow.destination shouldEqual operation.destination
              opRow.pkh shouldEqual operation.pkh
              opRow.delegate shouldEqual operation.delegate
              opRow.balance shouldEqual operation.balance
              opRow.operationGroupHash shouldEqual operationGroup.hash.value
              opRow.blockHash shouldEqual operationBlock.metadata.hash.value
              opRow.blockLevel shouldEqual operationBlock.metadata.header.level
              opRow.timestamp shouldEqual operationBlock.metadata.header.timestamp
          }
      }


    }

    "write accounts" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val expectedCount = 3

      val block = generateBlockRows(1, testReferenceTime).head
      val accountsInfo = generateAccounts(expectedCount, BlockHash(block.hash), block.level)

      val writeAndGetRows = for {
        _ <- Tables.Blocks += block
        written <- sut.writeAccounts(accountsInfo)
        rows <- Tables.Accounts.result
      } yield (written, rows)

      val (stored, dbAccounts) = dbHandler.run(writeAndGetRows.transactionally).futureValue

      stored.value shouldBe expectedCount

      dbAccounts should have size expectedCount

      import org.scalatest.Inspectors._

      forAll(dbAccounts zip accountsInfo.accounts) {
        case (row, (id, account)) =>
          row.accountId shouldEqual id.id
          row.blockId shouldEqual block.hash
          row.blockLevel shouldEqual block.level
          row.manager shouldEqual account.manager
          row.spendable shouldEqual account.spendable
          row.delegateSetable shouldEqual account.delegate.setable
          row.delegateValue shouldEqual account.delegate.value
          row.counter shouldEqual account.counter
          row.script shouldEqual account.script.map(_.toString)
          row.balance shouldEqual account.balance
      }

    }

    "fail to write accounts if the reference block is not stored" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val accountsInfo = generateAccounts(howMany = 1, blockHash = BlockHash("no-block-hash"), blockLevel = 1)

      val resultFuture = dbHandler.run(sut.writeAccounts(accountsInfo))

      whenReady(resultFuture.failed) {
          _ shouldBe a [java.sql.SQLException]
      }
    }

    "fetch nothing if looking up a non-existent operation group by hash" in {
      dbHandler.run(sut.operationsForGroup("no-group-here")).futureValue shouldBe None
    }

    "fetch existing operations with their group on a existing hash" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head
      val ops = generateOperationRowsForGroup(block, group)

      val populateAndFetch = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
        result <- sut.operationsForGroup(group.hash)
      } yield (result, ids)

      val (Some((groupRow, operationRows)), operationIds) = dbHandler.run(populateAndFetch).futureValue

      groupRow.hash shouldEqual group.hash
      operationRows should have size ops.size
      operationRows.map(_.operationId).toList should contain theSameElementsAs operationIds

    }

    "compute correct average fees from stored operations" in {
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)
      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head

      // mu = 152.59625
      // std-dev = 331.4
      // the sample std-dev should be 354.3, using correction formula
      val fees = Seq(
        Some("35.23"), Some("12.01"), Some("2.22"), Some("150.01"), None, Some("1020.30"), Some("1.00"), None
      )
      val ops = wrapFeesWithOperationRows(fees, block, group)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
      } yield ids

      dbHandler.run(populate).futureValue should have size (fees.size)

      //expectations
      val (mu, sigma) = (153, 332)
      val latest = new Timestamp(ops.map(_.timestamp.getTime).max)

      val expected = AverageFees(
        low = 0,
        medium = mu,
        high = mu + sigma,
        timestamp = latest,
        kind = ops.head.kind
      )

      //check
      val feesCalculation = sut.calculateAverageFees(ops.head.kind)

      dbHandler.run(feesCalculation).futureValue.value shouldEqual expected

    }

    "return None when computing average fees for a kind with no data" in {
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)
      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head

      val fees = Seq.fill(3)(Some("1.0"))
      val ops = wrapFeesWithOperationRows(fees, block, group)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
      } yield ids

      dbHandler.run(populate).futureValue should have size (fees.size)

      //check
      val feesCalculation = sut.calculateAverageFees("undefined")

      dbHandler.run(feesCalculation).futureValue shouldBe None

    }

    "compute average fees only using the selected operation kinds" in {
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)
      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head

      val (selectedFee, ignoredFee) = (Some("1.0"), Some("1000.0"))

      val fees = Seq(selectedFee, selectedFee, ignoredFee, ignoredFee)

      //change kind for fees we want to ignore
      val ops = wrapFeesWithOperationRows(fees, block, group).map {
        case op if op.fee == ignoredFee => op.copy(kind = op.kind + "ignore")
        case op => op
      }

      val selection = ops.filter(_.fee == selectedFee)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
      } yield ids

      dbHandler.run(populate).futureValue should have size (fees.size)

      //expectations
      val mu = 1
      val latest = new Timestamp(selection.map(_.timestamp.getTime).max)

      val expected = AverageFees(
        low = mu,
        medium = mu,
        high = mu,
        timestamp = latest,
        kind = ops.head.kind
      )
      //check
      val feesCalculation = sut.calculateAverageFees(selection.head.kind)

      dbHandler.run(feesCalculation).futureValue.value shouldEqual expected

    }

    "return the default when fetching the latest block level and there's no block stored" in {
      val expected = -1
      val maxLevel = dbHandler.run(
        sut.fetchMaxBlockLevel
      ).futureValue

      maxLevel should equal(expected)
    }

    "fetch the latest block level when blocks are available" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val expected = 5
      val populateAndFetch = for {
        _ <- Tables.Blocks ++= generateBlockRows(expected, testReferenceTime)
        result <- sut.fetchMaxBlockLevel
      } yield result

      val maxLevel = dbHandler.run(populateAndFetch.transactionally).futureValue

      maxLevel should equal(expected)
    }

    "return the default when fetching the max account level and there's no account stored" in {
      val expected = -1
      val maxLevel = dbHandler.run(
        sut.fetchAccountsMaxBlockLevel
      ).futureValue

      maxLevel shouldEqual expected
    }

    "fetch accounts max block level based only on stored accounts" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val blocks = generateBlockRows(5, testReferenceTime)
      val blocksWithAccounts = blocks.take(2)
      val accounts = blocksWithAccounts flatMap {
        block => generateAccountRows(1, block)
      }

      val expected = blocksWithAccounts.map(_.level).max

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocks
        _ <- Tables.Accounts ++= accounts
        result <- sut.fetchAccountsMaxBlockLevel
      } yield result

      val maxLevel = dbHandler.run(populateAndTest.transactionally).futureValue

      maxLevel shouldEqual expected

    }

    "test old accounts purging" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val levels = 5

      //first prepare the data

      val blocks = generateBlockRows(levels, testReferenceTime)
      val accounts = blocks flatMap {
        block => generateAccountRows(1, block)
      }

      val populate = for {
        bs <- Tables.Blocks ++= blocks
        accs <- Tables.Accounts ++= accounts
      } yield (bs, accs)

      val (Some(storedBlocks), Some(storedAccounts)) = dbHandler.run(populate.transactionally).futureValue

      //take level 0 (genesis block) into account
      storedBlocks shouldEqual (levels + 1)
      storedAccounts shouldEqual (levels + 1)

      //purge
      val removed = dbHandler.run(sut.purgeOldAccounts()).futureValue

      removed shouldBe levels //only the last is left

      //check the db
      val counts = for {
        different <- Tables.Accounts.filterNot(_.blockLevel === BigDecimal(levels)).length.result
        same <- Tables.Accounts.filter(_.blockLevel === BigDecimal(levels)).length.result
      } yield (different, same)

      dbHandler.run(counts).futureValue shouldBe (0, 1)

    }

    "correctly verify when a block exists" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val blocks = generateBlockRows(1, testReferenceTime)
      val opGroups = generateOperationGroupRows(blocks: _*)
      val testHash = BlockHash(blocks.last.hash)

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocks
        _ <- Tables.OperationGroups ++= opGroups
        existing <- sut.blockExists(testHash)
        nonExisting <- sut.blockExists(BlockHash("bogus-hash"))
      } yield (existing, nonExisting)

      val (hit, miss) = dbHandler.run(populateAndTest.transactionally).futureValue

      hit shouldBe true
      miss shouldBe false

    }

    "say a block doesn't exist if it has no associated operation group" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val blocks = generateBlockRows(1, testReferenceTime)
      val testHash = BlockHash(blocks.last.hash)

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocks
        found <- sut.blockExists(testHash)
      } yield found

      val exists = dbHandler.run(populateAndTest.transactionally).futureValue
      exists shouldBe false

    }
  }

  //a stable timestamp reference if needed
  private lazy val testReferenceTime =
    new Timestamp(
      LocalDate.of(2018, 1, 1)
        .atStartOfDay
        .toEpochSecond(ZoneOffset.UTC)
    )

  //creates pseudo-random strings of given length, based on an existing [[Random]] generator
  private val alphaNumericGenerator =
    (random: Random) => random.alphanumeric.take(_: Int).mkString

  /* randomly populate a number of fees */
  private def generateFees(howMany: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[AverageFees] = {
    require(howMany > 0, "the test can generates a positive number of fees, you asked for a non positive value")

    val rnd = new Random(randomSeed.seed)

    (1 to howMany).map {
      current =>
        val low = rnd.nextInt(10)
        val medium = rnd.nextInt(10) + 10
        val high = rnd.nextInt(10) + 20
        AverageFees(
          low = low,
          medium = medium,
          high = high,
          timestamp = new Timestamp(startAt.getTime + current),
          kind = "kind"
        )
    }.toList
  }

  /* randomly generates a number of accounts with associated block data */
  private def generateAccounts(howMany: Int, blockHash: BlockHash, blockLevel: Int)(implicit randomSeed: RandomSeed): AccountsWithBlockHashAndLevel = {
    require(howMany > 0, "the test can generates a positive number of accounts, you asked for a non positive value")

    val rnd = new Random(randomSeed.seed)

    val accounts = (1 to howMany).map {
      currentId =>
        (AccountId(String valueOf currentId),
          Account(
            manager = "manager",
            balance = rnd.nextInt,
            spendable = true,
            delegate = AccountDelegate(setable = false, value = Some("delegate-value")),
            script = Some("script"),
            counter = currentId
          )
        )
    }.toMap

    AccountsWithBlockHashAndLevel(blockHash, blockLevel, accounts)
  }

  /* randomly populate a number of blocks based on a level range */
  private def generateBlocks(toLevel: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[Block] = {
    require(toLevel > 0, "the test can generate blocks up to a positive chain level, you asked for a non positive value")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    //same for all blocks
    val chainHash = generateHash(5)

    val startMillis = startAt.getTime

    def generateOne(level: Int, predecessorHash: BlockHash): Block =
      Block(
        BlockMetadata(
          "protocol",
          Some(chainHash),
          BlockHash(generateHash(10)),
          BlockHeader(
            level = level,
            proto = 1,
            predecessor = predecessorHash,
            timestamp = new Timestamp(startMillis + level),
            validationPass = 0,
            operations_hash = None,
            fitness = Seq.empty,
            context = s"context$level",
            signature = Some(s"sig${generateHash(10)}")
          )),
        operationGroups = List.empty
      )

    //we need a block to start
    val genesis = generateOne(0, BlockHash("genesis"))

    //use a fold to pass the predecessor hash, to keep a plausibility of sort
    (1 to toLevel).foldLeft(List(genesis)) {
      case (chain, lvl) =>
        val currentBlock = generateOne(lvl, chain.head.metadata.hash)
        currentBlock :: chain
    }.reverse

  }


  /* randomly populate a number of blocks based on a level range */
  private def generateBlockRows(toLevel: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[Tables.BlocksRow] = {
    require(toLevel > 0, "the test can generate blocks up to a positive chain level, you asked for a non positive value")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    //same for all blocks
    val chainHash = generateHash(5)

    val startMillis = startAt.getTime

    def generateOne(level: Int, predecessorHash: String): BlocksRow =
      BlocksRow(
      level = level,
      proto = 1,
      predecessor = predecessorHash,
      timestamp = new Timestamp(startMillis + level),
      validationPass = 0,
      fitness = "fitness",
      protocol = "protocol",
      context = Some(s"context$level"),
      signature = Some(s"sig${generateHash(10)}"),
      chainId = Some(chainHash),
      hash = generateHash(10)
    )

    //we need somewhere to start with
    val genesis = generateOne(0, "genesis")

    //use a fold to pass the predecessor hash, to keep a plausibility of sort
    (1 to toLevel).foldLeft(List(genesis)) {
      case (chain, lvl) =>
        val currentBlock = generateOne(lvl, chain.head.hash)
        currentBlock :: chain
    }.reverse

  }

  /* create an operation group for each block passed in, using random values, with the requested copies of operations */
  private def generateOperationGroup(block: Block, operations: Int)(implicit randomSeed: RandomSeed): OperationGroup = {
    require(operations >= 0, "the test won't generate a negative number of operations")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    def fillOperations(): Option[List[Operation]] = {
      val ops = List.fill(operations) {
        Operation(
          kind = "kind",
          block = Some(block.metadata.hash.value),
          level = Some(block.metadata.header.level),
          slots = Some(List(0)),
          nonce = Some(generateHash(10)),
          op1 = None,
          op2 = None,
          bh1 = None,
          bh2 = None,
          pkh = Some("pkh"),
          secret = Some("secret"),
          proposals = Some(List("proposal")),
          period = Some("period"),
          source = Some("source"),
          proposal = Some("proposal"),
          ballot = Some("ballot"),
          fee = Some("fee"),
          counter = Some(0),
          gasLimit = Some("gasLimit"),
          storageLimit = Some("storageLimit"),
          publicKey = Some("publicKey"),
          amount = Some("amount"),
          destination = Some("destination"),
          parameters = Some("parameters"),
          managerPubKey = Some("managerPubKey"),
          balance = Some("balance"),
          spendable = Some(true),
          delegatable = Some(true),
          delegate = Some("delegate")
        )
      }
      if (ops.isEmpty) None else Some(ops)
    }


    OperationGroup(
      protocol = "protocol",
      chain_id = block.metadata.chain_id,
      hash = OperationHash(generateHash(10)),
      branch = generateHash(10),
      signature = Some(s"sig${generateHash(10)}"),
      contents = fillOperations()
    )
  }


  /* create an empty operation group for each block passed in, using random values */
  private def generateOperationGroupRows(blocks: BlocksRow*)(implicit randomSeed: RandomSeed): List[Tables.OperationGroupsRow] = {
    require(blocks.nonEmpty, "the test won't generate any operation group without a block to start with")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    blocks.map(
      block =>
        Tables.OperationGroupsRow(
          protocol = "protocol",
          chainId = block.chainId,
          hash = generateHash(10),
          branch = generateHash(10),
          signature = Some(s"sig${generateHash(10)}"),
          blockId = block.hash
        )
    ).toList
  }

  /* create operations related to a specific group, with random data */
  private def generateOperationRowsForGroup(block: BlocksRow, group: OperationGroupsRow, howMany: Int = 3): List[Tables.OperationsRow] =
   if (howMany > 0) {

     (1 to howMany).map {
       counting =>
         Tables.OperationsRow(
           kind = "operation-kind",
           operationGroupHash = group.hash,
           operationId = -1,
           blockHash = block.hash,
           timestamp = block.timestamp,
           blockLevel = block.level
         )
     }.toList
   } else List.empty

  /* create operation rows to hold the given fees */
  private def wrapFeesWithOperationRows(
    fees: Seq[Option[String]],
    block: BlocksRow,
    group: OperationGroupsRow) = {

    fees.zipWithIndex.map {
      case (fee, index) =>
        OperationsRow(
          kind = "kind",
          operationGroupHash = group.hash,
          operationId = -1,
          fee = fee,
          blockHash = block.hash,
          timestamp = new Timestamp(block.timestamp.getTime + index),
          blockLevel = block.level
        )
    }

  }

  /* randomly generates a number of account rows for some block */
  private def generateAccountRows(howMany: Int, block: BlocksRow): List[AccountsRow] = {
    require(howMany > 0, "the test can generates a positive number of accounts, you asked for a non positive value")

    (1 to howMany).map {
      currentId =>
        AccountsRow(
          accountId = String valueOf currentId,
          blockId = block.hash,
          blockLevel = block.level,
          manager = "manager",
          spendable = true,
          delegateSetable = false,
          delegateValue = None,
          counter = 0,
          script = None,
          balance = 0
        )
    }.toList

  }


}
