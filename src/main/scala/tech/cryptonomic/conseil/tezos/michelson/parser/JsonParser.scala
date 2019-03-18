package tech.cryptonomic.conseil.tezos.michelson.parser

import io.circe.parser.decode
import cats.syntax.functor._
import io.circe.{HCursor, _}
import io.circe.generic.auto._
import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonElement, _}

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
   *
   * We can distinguish which JsonSection it is by looking at its args. JsonTypeSection contains a single sequence, since
   * JsonExpressionSection contains an embedded one.
   *
   * */
  sealed trait JsonSection

  case class JsonExpressionSection(prim: String, args: List[JsonExpression]) extends JsonSection {
    def toMichelsonExpression: Option[MichelsonExpression] = args.headOption.map(_.toMichelsonExpression)
  }

  case class JsonCodeSection(prim: String, args: List[List[JsonInstruction]]) extends JsonSection {
    def toMichelsonCode = MichelsonCode(args.flatten.map(_.toMichelsonInstruction))
  }

  sealed trait JsonExpression {
    def toMichelsonExpression: MichelsonExpression
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
   * */
  case class JsonType(prim: String, args: Option[List[JsonExpression]]) extends JsonExpression {
    override def toMichelsonExpression = MichelsonType(prim, args.getOrElse(List.empty).map(_.toMichelsonExpression))
  }

  /*
   * Wrapper for int constant
   *
   * {"int": "0"}
   *
   * */
  case class JsonIntConstant(int: String) extends JsonExpression {
    override def toMichelsonExpression = MichelsonIntConstant(int.toLong)
  }

  /*
   * Wrapper for string constant
   *
   * {"string": "0"}
   *
   * */
  case class JsonStringConstant(string: String) extends JsonExpression {
    override def toMichelsonExpression = MichelsonStringConstant(string)
  }

  case class JsonEmptyExpression() extends JsonExpression {
    override def toMichelsonExpression: MichelsonExpression = MichelsonEmptyExpression
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

  case class JsonSimpleInstruction(prim: String, args: Option[List[JsonExpression]] = None) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonSimpleInstruction(prim, args.map(_.map(_.toMichelsonExpression)).getOrElse(List.empty))
  }

  case class JsonComplexInstruction(prim: String, args: List[List[JsonInstruction]]) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonComplexInstruction(prim, args.map {
      case Nil => MichelsonEmptyInstruction
      case it => MichelsonInstructionSequence(it.map(_.toMichelsonInstruction))
    })
  }

  case class JsonInstructionSequence(instructions: List[JsonInstruction]) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonInstructionSequence(instructions.map(_.toMichelsonInstruction))
  }

  case class ParserError(message: String) extends Throwable(message)

  type Result[T] = Either[Throwable, T]

  case class JsonSchema(code: List[JsonSection]) {
    def toMichelsonSchema: Result[MichelsonSchema] = for {
      parameter <- extractExpression("parameter")
      storage <- extractExpression("storage")
      code <- extractCode("code")
    } yield MichelsonSchema(parameter, storage, code)

    private def extractExpression(sectionName: String): Result[MichelsonExpression] = {
      code
        .collectFirst {
          case it@JsonExpressionSection(`sectionName`, _) => it
        }
        .flatMap(_.toMichelsonExpression)
        .toRight(ParserError(s"No expression $sectionName found"))
    }

    private def extractCode(sectionName: String): Result[MichelsonCode] = {
      code
        .collectFirst {
          case it@JsonCodeSection(`sectionName`, _) => it.toMichelsonCode
        }
        .toRight(ParserError(s"No code $sectionName found"))
    }
  }

  object GenericDerivation {
    implicit val decodeSection: Decoder[JsonSection] =
      List[Decoder[JsonSection]](
        Decoder[JsonCodeSection].widen,
        Decoder[JsonExpressionSection].widen
      ).reduceLeft(_ or _)

    implicit val decodeExpression: Decoder[JsonExpression] =
      List[Decoder[JsonExpression]](
        Decoder[JsonType].widen,
        Decoder[JsonIntConstant].widen,
        Decoder[JsonStringConstant].widen,
        Decoder[JsonEmptyExpression].widen
      ).reduceLeft(_ or _)

    implicit val decodeInstruction: Decoder[JsonInstruction] = cursor => {
      lazy val isSequence = (_: HCursor).downArray.succeeded
      lazy val isEmptyArray = (_: Json).asArray.exists(_.isEmpty)
      lazy val isComplexInstruction = (_: HCursor).downField("args").downArray.find(it => isSequence(it.hcursor) || isEmptyArray(it)).succeeded

      if (isSequence(cursor))
        cursor.as[List[JsonInstruction]].map(JsonInstructionSequence)
      else if (isComplexInstruction(cursor))
        cursor.as[JsonComplexInstruction]
      else
        cursor.as[JsonSimpleInstruction]
    }
  }

  trait Parser[T <: MichelsonElement] {
    def parse(json: String): Result[T]
  }

  implicit val michelsonInstructionParser: Parser[MichelsonInstruction] = {
    import GenericDerivation._
    decode[JsonInstruction](_).map(_.toMichelsonInstruction)
  }

  implicit val michelsonExpressionParser: Parser[MichelsonExpression] = (json: String) => {
    import GenericDerivation._
    decode[JsonExpression](json).map(_.toMichelsonExpression)
  }

  implicit val michelsonSchemaParser: Parser[MichelsonSchema] = (json: String) => {
    import GenericDerivation._
    decode[List[JsonSection]](json).map(JsonSchema).flatMap(_.toMichelsonSchema)
  }

  implicit val michelsonCodeParser: Parser[MichelsonCode] = (json: String) => {
    import GenericDerivation._
    decode[List[JsonInstruction]](json).map(instructions => MichelsonCode(instructions.map(_.toMichelsonInstruction)))
  }

  /* Parses Michelson Expression represented as JSON to domain objects */
  def parse[T <: MichelsonElement:Parser](json: String): Result[T] = {
    implicitly[Parser[T]].parse(json)
  }
}
