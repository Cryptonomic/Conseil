package tech.cryptonomic.conseil.indexer.tezos.michelson.dto

sealed trait MichelsonExpression extends MichelsonElement

/*
 * Class representing a type
 *
 * Examples:
 *
 *   unit
 *   |
 *   simple type represented as MichelsonType("prim")
 *
 *   (contract (or (option address) int))
 *    |        |    |               |
 *    |        |    |               single type "int"
 *    |        |    type "option" with one argument "address"
 *    |        type "or" with two arguments: "option address" and "int"
 *    type "contract" with one complex argument
 *
 *    The type above can be represented as below:
 *
 *    MichelsonType("contract", List(MichelsonType("or", List( MichelsonType("option", List( MichelsonType("address"))), MichelsonType("int")))))
 *
 *    (pair 0 {})
 *     |    | |
 *     |    |  MichelsonEmptyExpression
 *     |    MichelsonIntConstant
 *     MichelsonType "pair" with two arguments
 *
 *    The type above can be represented as below:
 *
 *    MichelsonType("pair", List(MichelsonIntConstant(0), MichelsonEmptyExpression))
 * */
case class MichelsonType(
    prim: String,
    args: List[MichelsonElement] = List.empty,
    annotations: List[String] = List.empty
) extends MichelsonExpression

/* Class representing an empty expression */
case object MichelsonEmptyExpression extends MichelsonExpression

case class MichelsonExpressionSequence(sequence: List[MichelsonExpression]) extends MichelsonExpression
