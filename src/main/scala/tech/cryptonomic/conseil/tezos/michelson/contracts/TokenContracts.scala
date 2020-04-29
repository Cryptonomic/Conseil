package tech.cryptonomic.conseil.tezos.michelson.contracts

import tech.cryptonomic.conseil.tezos.TezosTypes.{
  AccountId,
  Contract,
  ContractId,
  Decimal,
  Micheline,
  Parameters,
  ParametersCompatibility
}
import cats.implicits._
import scala.collection.immutable.TreeSet
import scala.util.Try
import scala.concurrent.SyncVar
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.util.CryptoUtil

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
object TokenContracts extends LazyLogging {

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
              account <- Codecs.decodeBigMapKey(key)
              code <- update.value
              balance <- MichelineOps.parseBalanceFromMap(code)
            } yield account -> balance
        }
      case "FA1.2-StakerDao" =>
        new TokenToolbox(id) {
          type PInfo = (String, AccountId)

          val parametersReader = compatWrapped =>
            MichelineOps.StakerDao.parseReceiverFromParameters(MichelineOps.handleCompatibility(compatWrapped))

          val balanceReader = (pinfo, update) =>
            for {
              (receiverKey, receiverAccountId) <- pinfo
              key <- MichelineOps.parseBytes(update.key)
              if key == receiverKey
              code <- update.value
              balance <- MichelineOps.StakerDao.parseBalanceFromMap(code)
            } yield receiverAccountId -> balance

        }
    }

  /** Builds a registry of token contracts with the token data passed-in
    *
    * @param knownTokens the pair of contract and standard used, the latter as a String
    */
  def fromConfig(knownTokens: List[(ContractId, String)]): TokenContracts = {
    logger.info("Creating a token registry from the following values: {}", knownTokens.map {
      case (cid, std) => cid.id -> std
    }.mkString(","))

    val tokens = knownTokens.flatMap {
      case (cid, std) => newToolbox(cid, std)
    }

    logger.info("The following token contracts were actually registered: {}", tokens.map(_.id.id).mkString(","))
    // we keep the token tools in a sorted set to speed up searching
    new TokenContracts(TreeSet(tokens: _*))
  }

  /* Will check if the possibly unavailable id registered matches with the value referred from the update */
  private def mapIdsMatch(registeredId: SyncVar[BigMapId], updateId: BigDecimal, token: ContractId): Boolean = {
    if (!registeredId.isSet)
      logger.error(
        """A token balance update was found where the map of the given token is not yet identified from contract origination
          | map_id: {}
          | token: {}""".stripMargin,
        updateId,
        token
      )
    registeredId.isSet && registeredId.get.id == updateId
  }

  /** Defines enc-dec operation used for token big maps */
  object Codecs {

    /** Tries to read the map id as an hex bytestring, and convert it to a valid account id */
    def decodeBigMapKey(hexEncoded: String): Option[AccountId] = {
      val id = CryptoUtil.readAddress(hexEncoded.trim()).map(AccountId)
      id.failed.foreach(
        err => logger.error("I failed to match a big map key as a proper account address", err)
      )
      id.toOption
    }
  }

  /* Defines extraction operations based on micheline fields */
  private object MichelineOps {
    import tech.cryptonomic.conseil.tezos.michelson.dto._
    import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser

    /* Lifts legacy parameters to the new format and then read the value */
    def handleCompatibility(compatLayer: ParametersCompatibility) =
      compatLayer.map(Parameters(_)).merge.value

    /* Defines staker dao specific operations */
    object StakerDao {

      /* Extract the receiver of a "transfer" operation from the parameters
       * the output includes the decoded account-id and the hex-encoded bytestring
       * We expect to use the bytes to extract balance updates from the big maps,
       * using the bytes as a key.
       */
      def parseReceiverFromParameters(paramCode: Micheline): Option[(String, AccountId)] = {
        val parsed = JsonParser.parse[MichelsonInstruction](paramCode.expression)

        parsed.left.foreach(
          err =>
            logger.error(
              """Failed to parse michelson expression for StakerDao receiver in parameters.
                | Code was: {}
                | Error is {}""".stripMargin,
              paramCode.expression,
              err.getMessage()
            )
        )

        //custom match to the contract call structure for a stakerdao transfer
        for {
          receiverBytes <- parsed.toOption.collect {
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
                        from :: MichelsonType(
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
              to
          }
          accountId <- Codecs.decodeBigMapKey(receiverBytes)
        } yield receiverBytes -> accountId
      }

      /* reads a map value as a list with a balance as head */
      def parseBalanceFromMap(mapCode: Micheline): Option[BigInt] = {

        val parsed = JsonParser.parse[MichelsonInstruction](mapCode.expression)

        parsed.left.foreach(
          err =>
            logger.error(
              """Failed to parse michelson expression for token balance extraction.
              | Code was: {}
              | Error is {}""".stripMargin,
              mapCode.expression,
              err.getMessage()
            )
        )

        parsed.toOption.collect {
          case MichelsonSingleInstruction("Pair", MichelsonIntConstant(balance) :: _, _) => balance
        }.flatMap { balance =>
          Try(BigInt(balance)).toOption
        }
      }
    }

    /* extracts a bytes value as a string if it corresponds to the micheline argument */
    def parseBytes(code: Micheline): Option[String] =
      JsonParser.parse[MichelsonInstruction](code.expression).toOption.collect {
        case MichelsonBytesConstant(bytes) => bytes
      }

    /* reads a map value as a list with a balance as head */
    def parseBalanceFromMap(mapCode: Micheline): Option[BigInt] = {

      val parsed = JsonParser.parse[MichelsonInstruction](mapCode.expression)

      parsed.left.foreach(
        err =>
          logger.error(
            """Failed to parse michelson expression for token balance extraction.
              | Code was: {}
              | Error is {}""".stripMargin,
            mapCode.expression,
            err.getMessage()
          )
      )

      parsed.toOption.collect {
        case MichelsonSingleInstruction("Pair", MichelsonIntConstant(balance) :: _, _) => balance
      }.flatMap { balance =>
        Try(BigInt(balance)).toOption
      }
    }

  }
}
