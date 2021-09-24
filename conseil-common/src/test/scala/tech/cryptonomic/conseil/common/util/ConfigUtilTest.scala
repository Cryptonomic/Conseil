package tech.cryptonomic.conseil.common.util

import java.sql.Timestamp
import java.time.Instant
import java.time.format.DateTimeFormatter

import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class ConfigUtilTest extends ConseilSpec {

  "ConfigUtil.Csv" should {
      import kantan.csv.generic._
      import shapeless._
      import ConfigUtil.Csv.timestampDecoder
      val format = DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)")

      //we add any missing implicit decoder for column types, not provided by default from the library

      val sut = ConfigUtil.Csv

      "read a csv source file and map to the corresponding rows for baker registry" in {

        //will provide the HList representation of the row, to be passed as a type for the method
        val genericRow = Generic[Tables.BakerRegistryRow]

        val rows: List[Tables.BakerRegistryRow] =
          sut
            .readRowsFromCsv[Tables.BakerRegistryRow, genericRow.Repr](
              csvSource = getClass.getResource("/tezos/baker_registry/testnet.csv"),
              separator = ','
            )
            .value

        rows should have size 1

        rows.head shouldBe Tables.BakerRegistryRow(
          name = "Money Every 3 Days",
          isAcceptingDelegation = Some(true),
          externalDataUrl = Some("https://moneyevery3days.com/moneyevery3days.json"),
          split = Some(BigDecimal(0.96)),
          paymentAccounts = Some("tz1bd5Pn5DxPinvCtkeJmoneyYiLeUebvUa5"),
          minimumDelegation = Some(100000),
          payoutDelay = Some(0),
          payoutFrequency = Some(1),
          minimumPayout = Some(0),
          isCheap = Some(true),
          payForOwnBlocks = Some(true),
          payForEndorsements = Some(true),
          payGainedFees = Some(true),
          payForAccusationGains = Some(false),
          subtractLostDepositsWhenAccused = Some(false),
          subtractLostRewardsWhenAccused = Some(true),
          subtractLostFeesWhenAccused = Some(true),
          payForRevelation = Some(true),
          subtractLostRewardsWhenMissRevelation = Some(true),
          subtractLostFeesWhenMissRevelation = Some(true),
          compensateMissedBlocks = Some(false),
          payForStolenBlocks = Some(true),
          compensateMissedEndorsements = Some(false),
          compensateLowPriorityEndorsementLoss = Some(false),
          overdelegationThreshold = Some(100),
          subtractRewardsFromUninvitedDelegation = Some(true),
          recordManager = Some("null"),
          timestamp =
            Timestamp.from(Instant.from(format.parse("Fri Feb 28 2020 09:20:46 GMT-0500 (Eastern Standard Time)")))
        )
      }

      "use a database table to find the csv file and map to the corresponding rows for baker registry" in {

        val rows: List[Tables.BakerRegistryRow] =
          sut
            .readTableRowsFromCsv(
              table = Tables.BakerRegistry,
              platform = "tezos",
              network = "testnet",
              separator = ','
            )
            .value

        rows should have size 1

        rows.head shouldBe Tables.BakerRegistryRow(
          name = "Money Every 3 Days",
          isAcceptingDelegation = Some(true),
          externalDataUrl = Some("https://moneyevery3days.com/moneyevery3days.json"),
          split = Some(BigDecimal(0.96)),
          paymentAccounts = Some("tz1bd5Pn5DxPinvCtkeJmoneyYiLeUebvUa5"),
          minimumDelegation = Some(100000),
          payoutDelay = Some(0),
          payoutFrequency = Some(1),
          minimumPayout = Some(0),
          isCheap = Some(true),
          payForOwnBlocks = Some(true),
          payForEndorsements = Some(true),
          payGainedFees = Some(true),
          payForAccusationGains = Some(false),
          subtractLostDepositsWhenAccused = Some(false),
          subtractLostRewardsWhenAccused = Some(true),
          subtractLostFeesWhenAccused = Some(true),
          payForRevelation = Some(true),
          subtractLostRewardsWhenMissRevelation = Some(true),
          subtractLostFeesWhenMissRevelation = Some(true),
          compensateMissedBlocks = Some(false),
          payForStolenBlocks = Some(true),
          compensateMissedEndorsements = Some(false),
          compensateLowPriorityEndorsementLoss = Some(false),
          overdelegationThreshold = Some(100),
          subtractRewardsFromUninvitedDelegation = Some(true),
          recordManager = Some("null"),
          timestamp =
            Timestamp.from(Instant.from(format.parse("Fri Feb 28 2020 09:20:46 GMT-0500 (Eastern Standard Time)")))
        )
      }

      "fail to read the csv data if the network doesn't have a matching config file" in {

        val rows: Option[List[Tables.RegisteredTokensRow]] =
          sut.readTableRowsFromCsv(table = Tables.RegisteredTokens, platform = "whatever", network = "nonsense")

        rows shouldBe empty

      }

      "fail to read the csv data if the file doesn't match the expected tabular format" in {

        //will provide the HList representation of the row, to be passed as a type for the method
        val genericRow = Generic[Tables.RegisteredTokensRow]

        val rows: List[Tables.RegisteredTokensRow] =
          sut
            .readRowsFromCsv[Tables.RegisteredTokensRow, genericRow.Repr](
              csvSource = getClass.getResource("/tezos/registered_tokens/testnet.csv"),
              separator = '|'
            )
            .value

        rows shouldBe empty

      }

    }
}
