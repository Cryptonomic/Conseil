package tech.cryptonomic.conseil.tezos.michelson.dto

sealed trait MichelsonExpression

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
 *    |        |    |                |
 *    |        |    |                single type "int"
 *    |        |    type "option" with one argument "adress"
 *    |        type "or" with two arguments: "option address" and "int"
 *    type "contract" with one complex argument
 *
 *    The type above can be represented as below:
 *
 *    MichelsonType("contract", List(MichelsonType("or", List( MichelsonType("option", List( MichelsonType("address"))), MichelsonType("int")))))
 * */
case class MichelsonType(prim: String, args: List[MichelsonExpression] = List.empty) extends MichelsonExpression

/* Class representing an int constant */
case class MichelsonIntConstant(int: Int) extends MichelsonExpression

/* Class representing a string constant */
case class MichelsonStringConstant(string: String) extends MichelsonExpression
