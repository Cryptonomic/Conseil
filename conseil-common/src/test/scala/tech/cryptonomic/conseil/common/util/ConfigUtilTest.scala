package tech.cryptonomic.conseil.common.util

import org.scalatest.{EitherValues, Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.common.tezos.Tables

class ConfigUtilTest extends WordSpec with Matchers with EitherValues with OptionValues {

  "ConfigUtil.Csv" should {
      import kantan.csv.generic._
      import shapeless._

      val sut = ConfigUtil.Csv

      "read a csv source file and map to the corresponding rows for token contracts" in {

        //will provide the HList representation of the row, to be passed as a type for the method
        val genericRow = Generic[Tables.RegisteredTokensRow]

        val rows: List[Tables.RegisteredTokensRow] =
          sut
            .readRowsFromCsv[Tables.RegisteredTokensRow, genericRow.Repr](
              csvSource = getClass.getResource("/registered_tokens/testnet.csv"),
              separator = '|'
            )
            .value

        rows should have size 1

        rows.head shouldBe Tables.RegisteredTokensRow(1, "USDTez", "FA1.2", "tz1eEnQhbwf6trb8Q8mPb2RaPkNk2rN7BKi9")
      }

      "use a database table to find the csv file and map to the corresponding rows for token contracts" in {

        val rows: List[Tables.RegisteredTokensRow] =
          sut
            .readTableRowsFromCsv(
              table = Tables.RegisteredTokens,
              network = "testnet",
              separator = '|'
            )
            .value

        rows should have size 1

        rows.head shouldBe Tables.RegisteredTokensRow(1, "USDTez", "FA1.2", "tz1eEnQhbwf6trb8Q8mPb2RaPkNk2rN7BKi9")
      }

      "fail to read the csv data if the network doesn't have a matching config file" in {

        val rows: Option[List[Tables.RegisteredTokensRow]] =
          sut.readTableRowsFromCsv(table = Tables.RegisteredTokens, network = "nonsense")

        rows shouldBe empty

      }

      "fail to read the csv data if the file doesn't match the expected tabular format" in {

        //will provide the HList representation of the row, to be passed as a type for the method
        val genericRow = Generic[Tables.RegisteredTokensRow]

        val rows: List[Tables.RegisteredTokensRow] =
          sut
            .readRowsFromCsv[Tables.RegisteredTokensRow, genericRow.Repr](
              csvSource = getClass.getResource("/registered_tokens/testnet.csv")
            )
            .value

        rows shouldBe empty

      }

    }
}
