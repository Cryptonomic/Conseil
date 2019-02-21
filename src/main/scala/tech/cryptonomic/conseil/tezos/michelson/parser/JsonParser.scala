package tech.cryptonomic.conseil.tezos.michelson.parser

import io.circe.parser.decode
import cats.syntax.functor._
import io.circe.{HCursor, _}
import io.circe.generic.auto._
import tech.cryptonomic.conseil.tezos.michelson.dto._

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

  case class JsonTypeSection(prim: String, args: List[JsonType]) extends JsonSection {
    def toMichelsonType: Option[MichelsonType] = args.headOption.map(_.toMichelsonExpression)
  }

  case class JsonExpressionSection(prim: String, args: List[List[JsonInstruction]]) extends JsonSection {
    def toMichelsonExpression = MichelsonCode(args.flatten.map(_.toMichelsonInstruction))
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
  case class JsonType(prim: String, args: Option[List[JsonType]]) extends JsonExpression {
    override def toMichelsonExpression = MichelsonType(prim, args.getOrElse(List.empty).map(_.toMichelsonExpression))
  }

  /*
   * Wrapper for int constant
   *
   * {"int": "0"}
   *
   * */
  case class JsonIntConstant(int: String) extends JsonExpression {
    override def toMichelsonExpression = MichelsonIntConstant(int.toInt)
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
    override def toMichelsonInstruction = MichelsonComplexInstruction(prim, MichelsonInstructionSequence(args.flatten.map(_.toMichelsonInstruction)))
  }

  case class JsonInstructionSequence(instructions: List[JsonInstruction]) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonInstructionSequence(instructions.map(_.toMichelsonInstruction))
  }

  class ParserError(message: String) extends Throwable(message)

  type Result[T] = Either[Throwable, T]

  case class JsonDocument(code: List[JsonSection]) {
    def toMichelsonSchema: Result[MichelsonSchema] = for {
      parameter <- extractType("parameter")
      storage <- extractType("storage")
      code <- extractExpression("code")
    } yield MichelsonSchema(parameter, storage, code)

    private def extractType(sectionName: String): Result[MichelsonType] = {
      code
        .collectFirst {
          case it@JsonTypeSection(`sectionName`, _) => it
        }
        .flatMap(_.toMichelsonType)
        .toRight(new ParserError(s"No type $sectionName found"))
    }

    private def extractExpression(sectionName: String): Result[MichelsonCode] = {
      code
        .collectFirst {
          case it@JsonExpressionSection(`sectionName`, _) => it.toMichelsonExpression
        }
        .toRight(new ParserError(s"No expression $sectionName found"))
    }
  }

  object GenericDerivation {
    implicit val decodeSection: Decoder[JsonSection] =
      List[Decoder[JsonSection]](
        Decoder[JsonTypeSection].widen,
        Decoder[JsonExpressionSection].widen,
      ).reduceLeft(_ or _)

    implicit val decodeExpression: Decoder[JsonExpression] =
      List[Decoder[JsonExpression]](
        Decoder[JsonType].widen,
        Decoder[JsonIntConstant].widen,
        Decoder[JsonStringConstant].widen
      ).reduceLeft(_ or _)

    implicit val decodeInstruction: Decoder[JsonInstruction] = (cursor: HCursor) => {
      val isComplexInstruction = (_: HCursor).downField("args").downArray.downArray.succeeded
      val isSequence = (_: HCursor).downArray.succeeded

      if (isSequence(cursor))
        cursor.as[List[JsonInstruction]].map(JsonInstructionSequence)
      else if (isComplexInstruction(cursor))
        cursor.as[JsonComplexInstruction]
      else
        cursor.as[JsonSimpleInstruction]
    }
  }

  /* Parses Michelson Expression represented as JSON to domain objects */
  def parse(json: String): Result[MichelsonSchema] = {

    import GenericDerivation._

    decode[JsonDocument](json).flatMap(_.toMichelsonSchema)
  }
}
