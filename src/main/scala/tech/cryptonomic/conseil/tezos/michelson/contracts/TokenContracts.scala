package tech.cryptonomic.conseil.tezos.michelson.contracts

import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, Contract, ContractId, Decimal, ScriptId}
import tech.cryptonomic.conseil.tezos.TezosTypes.Micheline
import tech.cryptonomic.conseil.tezos.michelson.dto.{
  MichelsonInstruction,
  MichelsonIntConstant,
  MichelsonSingleInstruction
}
import cats.implicits._
import scala.util.Try
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.util.CryptoUtil

/** Collects custom token contracts structures and operations */
object TokenContracts extends LazyLogging {

  /** a key hash paired with a balance */
  type BalanceUpdate = (ScriptId, BigInt)

  /** typed wrapper to clarify the meaning of the numerical id */
  case class BigMapId(id: BigDecimal) extends AnyVal

  /** alias to the custom code that will read the new balance for a token from big map diffs */
  private type BalanceReader = Contract.BigMapUpdate => Option[BalanceUpdate]

  /* data structure wrapping useful information + functions to act on tokens
   *
   * mapId is a reference to the big map used to store data on balances
   * balanceReader is a function that will use a big map update and extract
   *   balance information from it
   */
  private case class TokenToolbox(
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
  private val registry: Map[ContractId, TokenToolbox] = Map(
    ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux") -> TokenToolbox(
          BigMapId(1718),
          KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux_Balance_Read
        )
  )

  //this code needs to take into account an input of available account ids and check each for a matching balance value
  private lazy val KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux_Balance_Read: BalanceReader = update =>
    for {
      code <- update.value
      balance <- MichelineOps.parseBalanceFromMap(code)
    } yield update.key_hash -> balance

  /** Does the Id reference a known token smart contract? */
  def isKnownToken(token: ContractId): Boolean = registry.contains(token)

  /** Extracts any available balance changes for a given reference contract representing a known token ledger
    *
    * @param token the id for a smart contract
    * @param diff the big map changes found in a transaction
    * @return a possible pair of an account and its new balance for a token associated to the passed-in contract
    */
  def readBalance(token: ContractId)(diff: Contract.BigMapUpdate): Option[BalanceUpdate] = diff match {
    //we're looking for known token ledgers based on the contract id and the specific map identified by a diff
    case update @ Contract.BigMapUpdate("update", _, _, Decimal(mapId), _) =>
      for {
        TokenToolbox(BigMapId(id), customReadBalance) <- registry.get(token)
        if mapId == id
        balanceChange <- customReadBalance(update)
      } yield balanceChange
    case _ =>
      None
  }

  /* Given the key hash stored in the big map and an account, checks if the
   * two match, after encoding the account address first.
   *
   * @param keyHash a b58check script hash, corresponding to a map key in bytes
   * @param accountReference the address we expect to match
   * @return true if encoding the address and the key gives the same result
   */
  def hashCheck(keyHash: ScriptId)(accountReference: AccountId): Boolean = {
    val check = Codecs.computeKeyHash(accountReference)

    check.failed.foreach(
      err =>
        logger.error(
          "I couldn't check big maps token updates. Failure to check hash encoding of the expected account against the map key",
          err
        )
    )

    check.exists(_ == keyHash.value)
  }

  /** Defines enc-dec operation used for token big maps */
  object Codecs {
    import scorex.util.encode.{Base16 => Hex}
    import scorex.crypto.hash.{Blake2b256 => Blake}

    /** Complete sequence to compute key_hash from a tz#|KT1... account address */
    def computeKeyHash(address: AccountId): Try[String] =
      for {
        packed <- CryptoUtil.packAddress(address.id)
        binary <- Hex.decode(packed)
        hashed <- encodeBigMapKey(binary)
      } yield hashed

    /** Takes the bytes for a map key and creates the key-hash */
    def encodeBigMapKey(bytes: Array[Byte]): Try[String] = {
      val hashed = Blake.hash(bytes).toSeq
      CryptoUtil.base58CheckEncode(hashed, "expr")
    }

  }

  /* Defines extraction operations based on micheline fields */
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

  }
}
