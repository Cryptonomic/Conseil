package tech.cryptonomic.conseil.tezos.michelson.contracts

import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, Contract, ContractId}
import tech.cryptonomic.conseil.tezos.TezosTypes.Micheline
import tech.cryptonomic.conseil.tezos.michelson.dto.{
  MichelsonBytesConstant,
  MichelsonInstruction,
  MichelsonIntConstant,
  MichelsonSingleInstruction
}
import cats.implicits._
import scala.util.Try
import tech.cryptonomic.conseil.tezos.TezosTypes.Decimal
import com.typesafe.scalalogging.LazyLogging

/** Collects custom token contracts structures and operations */
object TokenLedgers extends LazyLogging {

  type BalanceUpdate = (AccountId, BigInt)

  /** typed wrapper to clarify the meaning of the numerical id */
  case class BigMapId(id: BigDecimal) extends AnyVal

  /** alias to the custom code that will read the new balance for a token from big map diffs */
  private type BalanceReader = Contract.BigMapUpdate => Option[(AccountId, BigInt)]

  /* data structure wrapping useful information + functions to act on ledgers
   *
   * mapId is a reference to the big map used to store data on balances
   * balanceReader is a function that will use a big map update and extract
   *   balance information from it
   */
  private case class LedgerToolbox(
      mapId: BigMapId,
      balanceReader: BalanceReader
  )

  /** For each specific contract available we store a few
    * relevant bits of data useful to extract information
    * related to that specific contract shape
    *
    * We're assuming here that the risk of collision on contract addresses representing
    * tokens, even on different tezos networks, is actually zero.
    */
  private val ledgers: Map[ContractId, LedgerToolbox] = Map(
    ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux") -> LedgerToolbox(
          BigMapId(1718),
          KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux_Balance_Read
        )
  )

  //this code needs to take into account an input of available account ids and check each for a matching balance value
  private lazy val KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux_Balance_Read: BalanceReader = update =>
    (MichelineOps.parseBytes(update.key), BigMapOps.parseMapCode(update).flatMap(MichelineOps.parseBalanceFromMap _)).mapN {
      case (bytesKey, balance) => AccountId(bytesKey) -> balance
    }

  /** Extracts any available balance changes for a given reference contract representing a known token ledger
    *
    * @param token the id for a smart contract
    * @param diff the big map changes found in a transaction
    * @return a possible pair of an account and its new balance for a token associated to the passed-in contract
    */
  def readBalance(token: ContractId)(diff: Contract.BigMapUpdate): Option[BalanceUpdate] = diff match {
    //we're looking for known token ledgers based on the contract id and the specific map identified by a diff
    case update @ Contract.BigMapUpdate("update", _, _, Decimal(mapId), _) if ledgers.contains(token) =>
      for {
        LedgerToolbox(BigMapId(id), balanceExtract) <- ledgers get token
        if mapId == id
        balanceChange <- balanceExtract(update)
      } yield balanceChange
    case _ =>
      None
  }

  //common operations to read/extract data from big maps
  private object BigMapOps {

    /* we check the update value but consider it valid only if there's a matching account address as a map key */
    def parseMapCode(
        update: Contract.BigMapUpdate,
        accountCheck: Option[AccountId] = None
    ): Option[Micheline] =
      update match {
        case Contract.BigMapUpdate("update", key, _, mapId, code) =>
          //read the key, the map id (to check?), the value code...
          MichelineOps
            .parseBytes(key)
            .collect {
              case bytesEncoding if accountCheck.flatMap(MichelineOps.accountIdToBytes).forall(_ == bytesEncoding) =>
                code
            }
            .flatten
        case _ => None
      }

  }

  private object MichelineOps {

    import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser

    /* reads a map value as a list with a balance as head */
    def parseBalanceFromMap(mapCode: Micheline): Option[BigInt] = {
      val parsedCode = JsonParser
        .parse[MichelsonInstruction](mapCode.expression)

      parsedCode.left.foreach(
        err => logger.error("Failed to parse michelson expression for token balance extraction", err)
      )

      parsedCode.toOption.collect {
        case MichelsonSingleInstruction("Pair", MichelsonIntConstant(balance) :: _, _) => balance
      }.flatMap { balance =>
        Try(BigInt(balance)).toOption
      }
    }

    /* extracts a bytes value as a string if it corresponds to the micheline argument */
    def parseBytes(code: Micheline): Option[String] =
      JsonParser.parse[MichelsonInstruction](code.expression).toOption.collect {
        case MichelsonBytesConstant(bytes) => bytes
      }

    /* attempts to decode the account b58-check address as an hexadecimal bytes encoding in a string
     * TODO Rewrite this using scorex crypto utilities
     */
    def accountIdToBytes(accId: AccountId): Option[String] = {
      import scorex.util.encode.{Base16, Base58}
      import scorex.crypto.hash.{Blake2b256 => Blake}

      val address = accId.id

      def encodeHex(hex: String) =
        if (address.startsWith("tz1")) {
          Some(s"0000$hex")
        } else if (address.startsWith("tz2")) {
          Some(s"0001$hex")
        } else if (address.startsWith("tz3")) {
          Some(s"0002$hex")
        } else if (address.startsWith("KT1")) {
          Some(s"01${hex}00")
        } else {
          None
        }

      for {
        bs <- Base58.decode(address).toOption
        hex = Base16.encode(bs drop 3)
        res <- encodeHex(hex)
      } yield Blake.hash(res)

      None

    }

  }
}
