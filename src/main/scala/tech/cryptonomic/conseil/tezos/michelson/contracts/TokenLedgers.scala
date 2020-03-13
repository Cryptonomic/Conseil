package tech.cryptonomic.conseil.tezos.michelson.contracts

import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, Contract, ContractId, Decimal, ScriptId}
import tech.cryptonomic.conseil.tezos.TezosTypes.Micheline
import tech.cryptonomic.conseil.tezos.michelson.dto.{
  MichelsonBytesConstant,
  MichelsonInstruction,
  MichelsonIntConstant,
  MichelsonSingleInstruction
}
import cats.implicits._
import scala.util.Try
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
  private lazy val KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux_Balance_Read: BalanceReader = update => {
    val extractKey = MichelineOps.parseBytes(update.key)
    val extractBalance = BigMapOps.parseMapCode(update).flatMap(MichelineOps.parseBalanceFromMap)
    (extractKey, extractBalance).mapN {
      case (key, balance) => AccountId(key) -> balance
    }
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
        LedgerToolbox(BigMapId(id), balanceExtract) <- ledgers.get(token)
        if mapId == id
        balanceChange <- balanceExtract(update)
      } yield balanceChange
    case _ =>
      None
  }

  //common operations to read/extract data from big maps
  private object BigMapOps {

    /* Given the key hash stored in the big map and an account, checks if the
     * two match, after encoding the account address first.
     *
     * @param keyHash a b58check script hash, corresponding to a map key in bytes
     * @param accountReference the address we expect to match
     * @return true if encoding the address and the key gives the same result
     */
    private def hashCheck(keyHash: ScriptId)(accountReference: AccountId): Boolean = {
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

    /* We extract the update code but consider it valid only if there's
     * a matching account address as a map key, optionally.
     * We need to pass in some address extracted from the corresponding operation's
     * paramters call, to compare its encoding with that of the map's keys.
     *
     * @param update the map update object
     * @param accountCheck optionally pass in the expected account address
     *                     that the update might be referring to
     */
    def parseMapCode(
        update: Contract.BigMapUpdate,
        accountCheck: Option[AccountId] = None
    ): Option[Micheline] =
      if (accountCheck.forall(hashCheck(update.key_hash))) update.value
      else None

  }

  /** Defines enc-dec operation used for token big maps */
  object Codecs {
    import scorex.util.encode.{Base16 => Hex, Base58}
    import scorex.crypto.hash.{Sha256, Blake2b256 => Blake}

    /** Tries to decode an hex string into bytes */
    def readHex(hexEncoded: String): Try[Array[Byte]] = Hex.decode(hexEncoded)

    /** Simplified b58check decoder.
      *
      * It's derived from bitcoin encoding, ignoring the prefix identification byte.
      * reference: https://en.bitcoin.it/wiki/Base58Check_encoding
      * scala impl: https://github.com/ACINQ/bitcoin-lib/blob/master/src/main/scala/fr/acinq/bitcoin/Base58.scala
      */
    def b58CheckDecode(input: String): Try[Array[Byte]] = Base58.decode(input).map(_.dropRight(4))

    /** Simplified b58check encoder.
      *
      * see also [[b58CheckDecode]] for additional refs
      */
    def b58CheckEncode(input: Array[Byte]): String = {
      val checksum = (Sha256.hash _ compose Sha256.hash)(input).take(4)
      Base58.encode(input ++ checksum)
    }

    /** Complete sequence to compute key_hash from a tz#|KT1... account address */
    def computeKeyHash(address: AccountId): Try[String] =
      for {
        packed <- packAddress(address)
        binary <- Hex.decode(packed)
        hashed <- encodeBigMapKey(binary)
      } yield hashed

    /** Decodes the account b58-check address as an hexadecimal bytestring */
    def packAddress(accId: AccountId): Try[String] = {
      val AccountId(b58encoded) = accId

      def dataLength(num: Long) = ("0000000" + num.toHexString).takeRight(8)

      def wrap(hexString: String): String =
        b58encoded.toLowerCase.take(3) match {
          case "tz1" => "0000" + hexString
          case "tz2" => "0001" + hexString
          case "tz3" => "0002" + hexString
          case "kt1" => "01" + hexString + "00"
        }

      //what if the wrapped length is odd?
      b58CheckDecode(b58encoded).map { bytes =>
        val wrapped = wrap(Hex.encode(bytes drop 3))
        s"050a${dataLength(wrapped.length / 2)}$wrapped"
      }
    }

    /** Takes the bytes for a map key and creates the key-hash */
    def encodeBigMapKey(bytes: Array[Byte]): Try[String] = {
      val hashed = Blake.hash(bytes)
      val hintEncode = "0d2c401b" + Hex.encode(hashed)
      Hex.decode(hintEncode).map(b58CheckEncode)
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

    /* Extracts the value of type "bytes" as a string if it corresponds to the micheline argument */
    def parseBytes(code: Micheline): Option[String] =
      JsonParser.parse[MichelsonInstruction](code.expression).toOption.collect {
        case MichelsonBytesConstant(bytes) => bytes
      }

  }
}
