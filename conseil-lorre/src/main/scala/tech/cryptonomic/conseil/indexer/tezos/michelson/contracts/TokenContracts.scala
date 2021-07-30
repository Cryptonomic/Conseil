package tech.cryptonomic.conseil.indexer.tezos.michelson.contracts

import java.lang.Integer.parseInt
import java.nio.charset.StandardCharsets

import tech.cryptonomic.conseil.common.tezos.TezosTypes.{
  makeAccountId,
  AccountId,
  Contract,
  ContractId,
  Decimal,
  Parameters,
  ParametersCompatibility
}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Micheline
import cats.implicits._

import scala.collection.immutable.TreeSet
import scala.util.Try
import scala.concurrent.SyncVar
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.util.CryptoUtil
import scorex.util.encode.{Base16 => Hex}
import tech.cryptonomic.conseil.indexer.tezos.michelson.dto.{MichelsonBytesConstant, MichelsonInstruction}
import tech.cryptonomic.conseil.indexer.tezos.michelson.parser.JsonParser

/** For each specific contract available we store a few
  * relevant bits of data useful to extract information
  * related to that specific contract shape.
  */
class TokenContracts(private val registry: Set[TokenContracts.TokenToolbox]) {
  import TokenContracts._

  /** Does the Id reference a known token smart contract? */
  def isKnownToken(token: ContractId): Boolean = registry.exists(_.id == token)

  /** Extracts any available balance changes for a given reference contract representing a known token ledger
    *
    * @param token the id for a smart contract
    * @param diff the big map changes found in a transaction
    * @param params the optional parameters passed in the transaction
    * @return a possible pair of an account and its new balance for a token associated to the passed-in contract
    */
  def readBalance(token: ContractId)(
      diff: Contract.BigMapUpdate,
      params: Option[ParametersCompatibility] = None
  ): Option[BalanceUpdate] =
    diff match {
      //we're looking for known token ledgers based on the contract id and the specific map identified by a diff
      case update @ Contract.BigMapUpdate("update", _, _, Decimal(updateMapId), _) =>
        for {
          toolbox @ TokenToolbox(_, registryId) <- registry.find(_.id == token)
          if mapIdsMatch(registryId, updateMapId, token)
          balanceChange <- toolbox.balanceReader(params.flatMap(toolbox.parametersReader), update)
        } yield balanceChange
      case _ =>
        None
    }

  /** Call this to store a big-map-id associated with a token contract.
    * This is supposed to happen once the chain records a block originating
    * one of the contracts identified via [[isKnownToken]].
    * This will be needed to identify the right map tracking token operation
    * updates, if more than one has been updated.
    *
    * @param token the contract identifier
    * @param id the id of the map used to store tokens
    */
  def setMapId(token: ContractId, id: BigDecimal): Unit =
    registry.find(_.id == token).foreach {
      case TokenToolbox(_, syncMapId) =>
        syncMapId.put(BigMapId(id))
    }
}

/** Collects custom token contracts structures and operations */
object TokenContracts extends ConseilLogSupport {

  /** a key address paired with a balance */
  type BalanceUpdate = (AccountId, BigInt)

  /** typed wrapper to clarify the meaning of the numerical id */
  case class BigMapId(id: BigDecimal) extends AnyVal

  /** alias to the custom code that will read the new balance for a token from big map diffs */
  private type BalanceReader[PInfo] = (Option[PInfo], Contract.BigMapUpdate) => Option[BalanceUpdate]

  private type ParamsReader[PInfo] = ParametersCompatibility => Option[PInfo]

  /* Data structure wrapping useful information + functions to act on tokens.
   * The value of the map id is not initially known, until the chain originates the
   * contract that refers to this toolbox, creating the id itself.
   *
   * mapId is a reference to the big map used to store data on balances, in a [[SyncVar]]
   * balanceReader is a function that will use a big map update and extract
   *   balance information from it
   */
  abstract private case class TokenToolbox(
      id: ContractId,
      mapId: SyncVar[BigMapId] = new SyncVar()
  ) {
    type PInfo
    def parametersReader: ParamsReader[PInfo]
    def balanceReader: BalanceReader[PInfo]
  }

  //we sort toolboxes by the contract id
  implicit private def toolboxOrdering: Ordering[TokenToolbox] = Ordering.by(_.id.id)

  /* Creates a new toolbox, only if the standard is a known one, or returns an empty Option */
  private def newToolbox(id: ContractId, standard: String): Option[TokenToolbox] =
    PartialFunction.condOpt(standard) {
      case "FA1.2" =>
        new TokenToolbox(id) {
          type PInfo = Nothing
          //parameters are not used for this kind of contract
          val parametersReader = Function.const(Option.empty)
          // the extraction code makes no check about the correctness of the key_hash
          // wrt. any potential account hash, which must be done somewhere else
          val balanceReader = (_, update) =>
            for {
              key <- MichelineOps.parseBytes(update.key)
              accountId <- Codecs.decodeBigMapKey(key)
              code <- update.value
              balance <- MichelineOps.parseBalanceFromMap(code)
            } yield accountId -> balance
        }
      case "FA1.2-StakerDao" =>
        new TokenToolbox(id) {
          type PInfo = Map[String, AccountId]

          val parametersReader = compatWrapped =>
            MichelineOps.StakerDao.parseAccountsFromParameters(MichelineOps.handleCompatibility(compatWrapped))

          val balanceReader = (pinfo, update) =>
            for {
              keysToAccountMap <- pinfo
              key <- MichelineOps.parseBytes(update.key)
              accountId <- keysToAccountMap.get(key)
              code <- update.value
              balance <- MichelineOps.parseBalanceFromMap(code)
            } yield accountId -> balance

        }
    }

  /** Builds a registry of token contracts with the token data passed-in
    *
    * @param knownTokens the pair of contract and standard used, the latter as a String
    */
  def fromConfig(knownTokens: List[(ContractId, String)]): TokenContracts = {
    val showTokens =
      knownTokens.map {
        case (cid, std) => cid.id -> std
      }.mkString(", ")
    logger.info(s"Creating a token registry from the following values: $showTokens")

    val tokens = knownTokens.flatMap {
      case (cid, std) => newToolbox(cid, std)
    }

    val showRegistered = tokens.map(_.id.id).mkString(", ")
    logger.info(s"The following token contracts were actually registered: $showRegistered")

    // we keep the token tools in a sorted set to speed up searching
    new TokenContracts(TreeSet(tokens: _*))
  }

  /* Will check if the possibly unavailable id registered matches with the value referred from the update */
  private def mapIdsMatch(registeredId: SyncVar[BigMapId], updateId: BigDecimal, token: ContractId): Boolean = {
    if (!registeredId.isSet)
      logger.error(
        s"""A token balance update was found where the map of the given token is not yet identified from contract origination
          | map_id: $updateId
          | token: $token""".stripMargin
      )
    registeredId.isSet && registeredId.get.id == updateId
  }

  /** Defines enc-dec operation used for token big maps */
  object Codecs {

    /* Michelson can be binary-encoded, in this case representing the
     * string value "totalSupply"
     *
     * We need to keep this variable Title-cased to correctly pattern match on it!
     * Scala puzzlers warning
     */
    private val LedgerTotalSupply = "05010000000b746f74616c537570706c79"

    /* Michelson can be binary-encoded, in this case representing the following
     * example expression:
     * bytes-tag  prim  pair-opcode   string-lit  len-bytes     "ledger"     raw-bytes
     *    05       07       07            01      00000006     6c6564676572     0a
     *
     * What would follow is the length and content of raw bytes, which encodes the account address, e.g.
     *  len-bytes: 44    encoding the address: tz1YWeZqt67XGHUvPFBmfMfWAoZtXELTWtKh
     *    00000016             00008d34410a9ccfa23728e02ca58cfaeb67b69d99fb
     */
    private val ledgerEntryBinaryPrefix = "05070701000000066c65646765720a"

    /** Tries to read the map id as an hex bytestring, and convert it to a valid account id
      * We need to account for different bytes encodings on different token formats
      */
    def decodeBigMapKey(hexEncoded: String): Option[AccountId] = hexEncoded.trim match {
      case LedgerTotalSupply =>
        //this is a constant value for tzBTC, we can ignore it for now
        Option.empty
      case ledgerEntry if ledgerEntry.startsWith(ledgerEntryBinaryPrefix) =>
        //this encodes the address as michelson code representing a "ledger" pair entry
        val id = (MichelineOps.decodeLedgerAccount _).tupled(
          ledgerEntry.drop(ledgerEntryBinaryPrefix.size).splitAt(8)
        )
        id.failed.foreach(
          err => logger.error("I failed to match a big map key as a proper account address", err)
        )
        id.toOption
      case packedAddress if packedAddress.startsWith("0") =>
        //this is directly the packed addres
        val id = CryptoUtil.readAddress(packedAddress).map(makeAccountId)
        id.failed.foreach(
          err => logger.error("I failed to match a big map key as a proper account address", err)
        )
        id.toOption
    }
  }

  /* Defines extraction operations based on micheline fields */
  private object MichelineOps {
    import tech.cryptonomic.conseil.indexer.tezos.michelson.dto._
    import tech.cryptonomic.conseil.indexer.tezos.michelson.parser.JsonParser

    /* Lifts legacy parameters to the new format and then read the value */
    def handleCompatibility(compatLayer: ParametersCompatibility) =
      compatLayer.map(Parameters(_)).merge.value

    /* Defines staker dao specific operations */
    object StakerDao {

      /* Extract the accounts involved in a "transfer" operation from the parameters.
       * The output includes the decoded account-id and the hex-encoded bytestring
       * We expect to use the bytes to extract balance updates from the big maps,
       * using the bytes as a key.
       * WARNING: this might not be needed anymore since we can probably decode
       * the encoded bytes directly to read the account addresses from big map diffs.
       */
      def parseAccountsFromParameters(paramCode: Micheline): Option[Map[String, AccountId]] = {
        val parsed = JsonParser.parse[MichelsonInstruction](paramCode.expression)

        parsed.left.foreach(
          err =>
            logger.error(
              s"""Failed to parse michelson expression for StakerDao receiver in parameters.
                | Code was: ${paramCode.expression}
                | Error is ${err.getMessage}""".stripMargin,
              err
            )
        )

        parsed.foreach(
          michelson => logger.debug(s"I parsed a staker dao parameters value as $michelson")
        )

        /* Custom match to the contract call structure for a stakerdao transfer
         * the contract has many entrypoints, the one that we're interested in is
         * at the sub-path: right-left-left-right and looks like
         * pair %transfer (address :from) (pair (address :to) (nat :value))
         */
        for {
          (senderBytes, receiverBytes) <- parsed.toOption.collect {
            case MichelsonSingleInstruction(
                "Right",
                MichelsonType(
                  "Left",
                  MichelsonType(
                    "Left",
                    MichelsonType(
                      "Right",
                      MichelsonType(
                        "Pair",
                        MichelsonBytesConstant(from) ::
                            MichelsonType(
                              "Pair",
                              MichelsonBytesConstant(to) :: _,
                              _
                            ) :: _,
                        _
                      ) :: _,
                      _
                    ) :: _,
                    _
                  ) :: _,
                  _
                ) :: _,
                _
                ) =>
              (from, to)
          }
          (senderId, receiverId) <- (Codecs.decodeBigMapKey(senderBytes), Codecs.decodeBigMapKey(receiverBytes)).tupled
        } yield
          Map(
            senderBytes -> senderId,
            receiverBytes -> receiverId
          )

      }

    }

    /* extracts a bytes value as a string if it corresponds to the micheline argument */
    def parseBytes(code: Micheline): Option[String] =
      JsonParser.parse[MichelsonInstruction](code.expression).toOption.collect {
        case MichelsonBytesConstant(bytes) => bytes
      }

    def decodeLedgerAccount(hexLength: String, hexAccount: String): Try[AccountId] =
      for {
        //this is number of bytes, each hex byte will take 2 chars
        length <- Hex.decode(hexLength).map(BigInt(_).toInt)
        accountId <- CryptoUtil.readAddress(hexAccount.take(length * 2))
      } yield makeAccountId(accountId)

    /* Michelson can be binary-encoded, in this case representing the following
     * example expression:
     * bytes-tag  prim  pair-opcode   int-lit    zarith-bigint   empty array
     *   05        07       07          00        86-bb-23       0200000000
     */
    private val ledgerValueBinaryPrefix = "05070700"

    /** Reads a map value as a balance number, encoded in different forms
      * depending on the specific ledger
      */
    def parseBalanceFromMap(mapCode: Micheline): Option[BigInt] = {

      val parsed = JsonParser.parse[MichelsonInstruction](mapCode.expression)

      parsed.left.foreach(
        err =>
          logger.error(
            s"""Failed to parse michelson expression for token balance extraction.
              | Code was: ${mapCode.expression}
              | Error is ${err.getMessage}""".stripMargin,
            err
          )
      )

      parsed.foreach(
        michelson => logger.debug(s"I parsed a token contract diff value as $michelson")
      )

      parsed.toOption.collect {
        case MichelsonIntConstant(balance) =>
          //straightforward value out of the map: e.g. staker-dao
          Try(BigInt(balance))
        case MichelsonSingleInstruction("Pair", MichelsonIntConstant(balance) :: _, _) =>
          //here we have the simplest indirect form: e.g. USDtz
          Try(BigInt(balance))
        case MichelsonBytesConstant(bytestring) if bytestring.startsWith(ledgerValueBinaryPrefix) =>
          /* a little more complex handling if the ledger value is encoded in bytes: e.g. tzBTC
           * the code is the same as the USDtz case but byte-encoded
           * Thus the prefix corresponds to: Pair (int :balance) array
           *
           * We consider only the balance, while the remaining array, which can be empty,
           * might represent a map of "spender approvals", where one token holder allows other
           * accounts to exchange a part of their balance
           */
          CryptoUtil.decodeZarithNumber(bytestring.drop(ledgerValueBinaryPrefix.size))
      }.flatMap(_.toOption)
    }

  }

  object Tzip16 {

    /** Helper function for decoding hex string to UTF8 string */
    private def proceduralDecode(hex: String): String = {
      val bytes = new Array[Byte](hex.length / 2)
      var i = 0
      while (i < bytes.length) {
        bytes(i) = parseInt(hex.substring(i * 2, i * 2 + 2), 16).toByte
        i += 1
      }
      new String(bytes, StandardCharsets.UTF_8)
    }

    def extractTzip16MetadataLocationFromParameters(
        paramCode: Micheline,
        path: Option[String],
        locationType: Option[String] = Some("ipfs")
    ): Option[String] = {
      val parsed = JsonParser.parse[MichelsonInstruction](paramCode.expression)

      parsed.left.foreach(
        err =>
          logger.error(
            s"""Failed to parse michelson expression for tzip-16 receiver in parameters.
               | Code was: ${paramCode.expression}
               | Error is ${err.getMessage}""".stripMargin,
            err
          )
      )

      parsed.foreach(
        michelson => logger.debug(s"I parsed a tzip-16 parameters value as $michelson")
      )

      val extractedLocation = locationType match {
        case Some("ipfs") =>
          parsed.toOption
            .flatMap(
              _.findInstruction(MichelsonBytesConstant(""), startsWith = Some("69706673")).sortBy(_.length).headOption
            )
        case Some("http") =>
          parsed.toOption
            .flatMap(
              _.findInstruction(MichelsonBytesConstant(""), startsWith = Some("68747470")).sortBy(_.length).headOption
            )
        case None => None
      }

      for {
        pth <- path.orElse(extractedLocation)
        metadataUrl <- parsed.toOption.flatMap(_.getAtPath(pth)).collect {
          case MichelsonBytesConstant(mu) =>
            mu
        }
      } yield proceduralDecode(metadataUrl)
    }
  }

}
