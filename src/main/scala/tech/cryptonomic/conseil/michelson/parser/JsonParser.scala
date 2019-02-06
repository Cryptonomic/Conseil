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

  case class Document(code: Seq[JsonSection])

  case class ParsingError(embeddedException: Exception)

  def parse(json: String): Either[ParsingError, MichelsonSchema] = {

    import GenericDerivation._

    for {
      document <- decode[Document](json).left.map(ParsingError)

      parameter <- extractType(document, "parameter")
      storage <- extractType(document, "storage")
      code <- extractExpression(document, "code")
    } yield MichelsonSchema(parameter, storage, code)
  }

  private def extractType(document: Document, parameter: String): Either[ParsingError, MichelsonType] = {
    document.code
      .collectFirst {
        case it: JsonTypeSection if it.prim == parameter => it
      }
      .flatMap(_.args.flatMap(_.headOption))
      .map(_.toMichelsonType)
      .toRight(ParsingError(new RuntimeException(s"No type $parameter found")))
  }

  private def extractExpression(document: Document, parameter: String): Either[ParsingError, MichelsonCode] = {
    document.code
      .collectFirst {
        case it: ExpressionSection if it.prim == parameter => it.toMichelsonExpression
      }
      .toRight(ParsingError(new RuntimeException(s"No expression $parameter found")))
  }
}
