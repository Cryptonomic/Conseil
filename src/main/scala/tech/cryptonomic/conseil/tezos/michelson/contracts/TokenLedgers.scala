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

/** Collects custom token contracts structures and operations */
object TokenLedgers {

  /** alias to the custom code that will read the new balance for a token from big map diffs */
  private type BalanceExtractor = Contract.BigMapUpdate => Option[(AccountId, BigInt)]

  type BalanceUpdate = (AccountId, BigInt)

  /** this extractor always returns an empty result and it's meant as a default fallback */
  private val emptyBalanceExtractor: BalanceExtractor =
    Function.const[Option[(AccountId, BigInt)], Contract.BigMapUpdate](Option.empty)

  /** typed wrapper to identify the meaning of the numerical id */
  case class BigMapId(id: BigDecimal) extends AnyVal

  /** For a specific token contract we define a way to read the new balance
    * on a transaction update, based on the BigMap contents
    * The map key is made up of
    * - a contract address that contains the token smart contract
    * - a big-map identifier that keeps the ledger data for the contract (used for double-checking)
    *
    * This implicitly assumes that, independently of the network used, the chance of collision, on
    * both contract address and map assignment, is negligible
    */
  private val customBalanceStorage: Map[(ContractId, BigMapId), BalanceExtractor] = Map(
    (ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux"), BigMapId(1718)) -> KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux_Ledger
  )

  //this code needs to take into account an input of available account ids and check each for a matching balance value
  private lazy val KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux_Ledger: BalanceExtractor = update =>
    (MichelineOps.parseBytes(update.key), BigMapOps.parseMapCode(update).flatMap(MichelineOps.parseBalanceFromMap _)).mapN {
      case (bytesKey, balance) => AccountId(bytesKey) -> balance
    }

  /** Extracts any available balance changes for a given reference contract representing a known token ledger
    *
    * @param token the id for a smart contract
    * @param diff the big map changes found in a transaction
    * @return a possible pair of an account and its new balance for a token associated to the passed-in contract
    */
  def readBalance(token: ContractId, diff: Contract.BigMapUpdate): Option[BalanceUpdate] = diff match {
    //we're looking for known token ledgers based on the contract id and the specific map identified by a diff
    case update @ Contract.BigMapUpdate("update", _, _, Decimal(mapId), _)
        if customBalanceStorage.keySet.contains(token -> BigMapId(mapId)) =>
      customBalanceStorage.getOrElse(token -> BigMapId(mapId), TokenLedgers.emptyBalanceExtractor)(update)
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
    def parseBalanceFromMap(mapCode: Micheline): Option[BigInt] =
      JsonParser
        .parse[MichelsonInstruction](mapCode.expression)
        .toOption
        .collect {
          case MichelsonSingleInstruction("PAIR", MichelsonIntConstant(balance) :: _, _) => balance
        }
        .flatMap { balance =>
          Try(BigInt(balance)).toOption
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
