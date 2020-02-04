package tech.cryptonomic.conseil.tezos.michelson.parser

import io.circe.parser.decode
import cats.syntax.functor._
import io.circe._
import io.circe.generic.auto._
import tech.cryptonomic.conseil.util.JsonUtil.{CirceCommonDecoders, JsonString}
import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonElement, _}
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser.EmbeddedElement.toMichelsonElement

import scala.collection.immutable.{List, Nil}

/* Parses Michelson Expression represented as JSON to domain objects */
object JsonParser {

  /*
   * Wrapper for json section
   *
   * {"code": [{"prim": "parameter", "args": [{...}]}, {"prim": "storage", "args": [{...}]}, {"prim": "code", "args": [[{...}]]}]}
   * |         |                              |        |                            |        |                          |
   * |         JsonTypeSection                JsonType JsonTypeSection              JsonType ExpressionSection          JsonInstruction
   * JsonDocument
   *
   * We can distinguish which JsonSection it is by looking at its args. JsonTypeSection contains a single sequence, since
   * JsonExpressionSection contains an embedded one.
   *
   * */
  sealed trait JsonSection

  case class JsonExpressionSection(prim: String, args: List[JsonExpression]) extends JsonSection {
    def toMichelsonExpression: Option[MichelsonExpression] = args.headOption.map(_.toMichelsonExpression)
  }

  case class JsonCodeSection(prim: String, args: Either[List[List[JsonInstruction]], List[JsonInstruction]])
      extends JsonSection {
    def toMichelsonCode: MichelsonCode = MichelsonCode(args.map(List(_)).merge.flatten.map(_.toMichelsonInstruction))
  }

  sealed trait JsonExpression {
    def toMichelsonExpression: MichelsonExpression
  }

  type EmbeddedElement = Either[Either[JsonExpression, JsonInstruction], List[JsonInstruction]]

  object EmbeddedElement {
    def toMichelsonElement(embeddedElement: EmbeddedElement): MichelsonElement = embeddedElement match {
      case Left(Left(jsonExpression)) => jsonExpression.toMichelsonExpression
      case Left(Right(jsonInstruction)) => jsonInstruction.toMichelsonInstruction.normalized
      case Right(Nil) => MichelsonEmptyInstruction
      case Right(jsonInstructions) => MichelsonInstructionSequence(jsonInstructions.map(_.toMichelsonInstruction))
    }
  }

  /*
   * Wrapper for type
   *
   * {"prim": "pair", "args": [{"prim": "int"}, {"prim": "address"}]}
   * |                         |                |
   * |                         |                single type "address"
   * |                         single type "int"
   * type "pair" with two arguments
   *
   * {"prim": "pair", "args": [{"prim": "int"}, []]}
   * |                         |                |
   * |                         |                empty expression
   * |                         single type "int"
   * type "pair" with two arguments
   *
   * Empty expression is represented as an empty array in JSON.
   *
   * */
  case class JsonType(
      prim: String,
      args: Option[List[EmbeddedElement]],
      annots: Option[List[String]] = None
  ) extends JsonExpression {
    override def toMichelsonExpression =
      MichelsonType(
        prim,
        args.getOrElse(List.empty).map(toMichelsonElement),
        annots.getOrElse(List.empty)
      )
  }

  /*
   * Wrapper for instruction
   *
   * [{"prim": "DIP", "args": [[{"prim": "DUP"}]]}, [{"prim": "DIP", "args": [[{"prim": "NIL", "args": [{"prim": "operation"}]}]]}]]
   *  |                         |                   ||                         |                        |
   *  JsonComplexInstruction    |                   |JsonComplexInstruction    JsonSimpleInstruction    JsonType
   *                            |                   |
   *                            |                   JsonInstructionSequence
   *                            JsonSimpleInstruction
   *
   * */
  sealed trait JsonInstruction {
    def toMichelsonInstruction: MichelsonInstruction
  }

  case class JsonSimpleInstruction(
      prim: String,
      args: Option[List[EmbeddedElement]] = None,
      annots: Option[List[String]] = None
  ) extends JsonInstruction {
    override def toMichelsonInstruction =
      MichelsonSingleInstruction(
        name = prim,
        annotations = annots.getOrElse(List.empty),
        embeddedElements = args.getOrElse(List.empty).map(toMichelsonElement)
      )
  }

  case class JsonInstructionSequence(instructions: List[JsonInstruction]) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonInstructionSequence(instructions.map(_.toMichelsonInstruction))
  }

  /*
   * Wrapper for int constant
   *
   * {"int": "0"}
   *
   * */
  case class JsonIntConstant(int: String) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonIntConstant(int)
  }

  /*
   * Wrapper for string constant
   *
   * {"string": "0"}
   *
   * */
  case class JsonStringConstant(string: String) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonStringConstant(string)
  }

  /*
   * Wrapper for bytes constant
   *
   * {"bytes": "0500"}
   *
   * */
  case class JsonBytesConstant(bytes: String) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonBytesConstant(bytes)
  }

  case class ParserError(message: String) extends Throwable(message)

  type Result[T] = Either[Throwable, T]

  case class JsonSchema(code: List[JsonSection]) {
    def toMichelsonSchema: Result[MichelsonSchema] =
      for {
        parameter <- extractExpression("parameter")
        storage <- extractExpression("storage")
        code <- extractCode
      } yield MichelsonSchema(parameter, storage, code)

    private def extractExpression(sectionName: String): Result[MichelsonExpression] =
      code.collectFirst {
        case it @ JsonExpressionSection(`sectionName`, _) => it
      }.flatMap(_.toMichelsonExpression)
        .toRight(ParserError(s"No expression $sectionName found"))

    private def extractCode: Result[MichelsonCode] =
      code.collectFirst {
        case it @ JsonCodeSection("code", _) => it.toMichelsonCode
      }.toRight(ParserError("No code section found"))
  }

  object GenericDerivation {
    implicit val decodeSection: Decoder[JsonSection] =
      List[Decoder[JsonSection]](
        Decoder[JsonCodeSection].ensure(_.prim == "code", "No code section found").widen,
        Decoder[JsonExpressionSection].widen
      ).reduceLeft(_ or _)

    implicit val decodeExpression: Decoder[JsonExpression] = Decoder[JsonType].widen

    val decodeInstructionSequence: Decoder[JsonInstructionSequence] =
      _.as[List[JsonInstruction]].map(JsonInstructionSequence)

    implicit val decodeInstruction: Decoder[JsonInstruction] =
      List[Decoder[JsonInstruction]](
        decodeInstructionSequence.widen,
        Decoder[JsonSimpleInstruction].widen,
        Decoder[JsonIntConstant].widen,
        Decoder[JsonStringConstant].widen,
        Decoder[JsonBytesConstant].widen
      ).reduceLeft(_ or _)

    implicit def decodeEither[A, B](implicit leftDecoder: Decoder[A], rightDecoder: Decoder[B]): Decoder[Either[A, B]] =
      CirceCommonDecoders.decodeUntaggedEither
  }

  trait Parser[T <: MichelsonElement] {
    def parse(json: String): Result[T]
  }

  implicit val michelsonInstructionParser: Parser[MichelsonInstruction] = {
    import GenericDerivation._
    decode[JsonInstruction](_).map(_.toMichelsonInstruction)
  }

  implicit val michelsonExpressionParser: Parser[MichelsonExpression] = {
    import GenericDerivation._
    decode[JsonExpression](_).map(_.toMichelsonExpression)
  }

  implicit val michelsonSchemaParser: Parser[MichelsonSchema] = {
    import GenericDerivation._
    decode[List[JsonSection]](_).flatMap {
      case Nil => Right(MichelsonSchema.empty)
      case jsonSections => JsonSchema(jsonSections).toMichelsonSchema
    }
  }

  implicit val michelsonCodeParser: Parser[MichelsonCode] = {
    import GenericDerivation._
    decode[List[JsonInstruction]](_).map(instructions => MichelsonCode(instructions.map(_.toMichelsonInstruction)))
  }

  /* Parses Michelson Expression represented as JSON to domain objects */
  def parse[T <: MichelsonElement: Parser](json: String): Result[T] =
    implicitly[Parser[T]].parse(JsonString sanitize json)
}
