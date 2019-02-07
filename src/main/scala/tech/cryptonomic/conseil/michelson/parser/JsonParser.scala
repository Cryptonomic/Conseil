package tech.cryptonomic.conseil.michelson.parser

import io.circe.parser.decode
import cats.syntax.functor._
import io.circe._
import io.circe.generic.auto._
import tech.cryptonomic.conseil.michelson.dto._

class JsonParser {

  sealed trait JsonSection

  case class JsonTypeSection(prim: String, args: Option[Seq[JsonTypeSection]]) extends JsonSection {
    def toMichelsonType: MichelsonType = MichelsonType(prim, args.getOrElse(Seq()).map(_.toMichelsonType))
  }

  case class ExpressionSection(prim: String, args: Seq[Seq[JsonInstruction]]) extends JsonSection {
    def toMichelsonExpression = MichelsonCode(args.flatten.map(_.toMichelsonInstruction))
  }

  sealed trait JsonInstruction {
    def toMichelsonInstruction: MichelsonInstruction
  }

  case class JsonSimpleInstruction(prim: String, args: Option[Seq[JsonTypeSection]] = None) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonSimpleInstruction(prim, args.flatMap(_.headOption.map(_.toMichelsonType)))
  }

  case class JsonComplexInstruction(prim: String, args: Seq[Seq[JsonInstruction]]) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonComplexInstruction(prim, MichelsonInstructionSequence(args.flatten.map(_.toMichelsonInstruction)))
  }

  case class JsonInstructionSequence(instructions: Seq[JsonInstruction]) extends JsonInstruction {
    override def toMichelsonInstruction = MichelsonInstructionSequence(instructions.map(_.toMichelsonInstruction))
  }

  class ParserError(message: String) extends Throwable(message)

  type Result[T] = Either[Throwable, T]

  case class JsonDocument(code: Seq[JsonSection]) {
    def toMichelsonSchema: Result[MichelsonSchema] = {
      for {
        parameter <- extractType("parameter")
        storage <- extractType("storage")
        code <- extractExpression("code")
      } yield MichelsonSchema(parameter, storage, code)
    }

    private def extractType(parameter: String): Result[MichelsonType] = {
      code
        .collectFirst {
          case it: JsonTypeSection if it.prim == parameter => it
        }
        .flatMap(_.args.flatMap(_.headOption))
        .map(_.toMichelsonType)
        .toRight(new ParserError(s"No type $parameter found"))
    }

    private def extractExpression(parameter: String): Result[MichelsonCode] = {
      code
        .collectFirst {
          case it: ExpressionSection if it.prim == parameter => it.toMichelsonExpression
        }
        .toRight(new ParserError(s"No expression $parameter found"))
    }
  }

  object GenericDerivation {
    implicit val decodeSection: Decoder[JsonSection] =
      List[Decoder[JsonSection]](
        Decoder[JsonTypeSection].widen,
        Decoder[ExpressionSection].widen
      ).reduceLeft(_ or _)

    implicit val decodeInstruction: Decoder[JsonInstruction] = (cursor: HCursor) => {
      def isComplexInstruction: HCursor => Boolean = _.downField("args").values.flatMap(_.headOption.flatMap(_.hcursor.values)).isDefined
      def isSequence: HCursor => Boolean = _.values.isDefined

      cursor match {
        case _ if isSequence(cursor) => cursor.as[Seq[JsonInstruction]].map(JsonInstructionSequence)
        case _ if isComplexInstruction(cursor) => cursor.as[JsonComplexInstruction]
        case _ => cursor.as[JsonSimpleInstruction]
      }
    }
  }

  def parse(json: String): Result[MichelsonSchema] = {

    import GenericDerivation._

    decode[JsonDocument](json).flatMap(_.toMichelsonSchema)
  }
}
