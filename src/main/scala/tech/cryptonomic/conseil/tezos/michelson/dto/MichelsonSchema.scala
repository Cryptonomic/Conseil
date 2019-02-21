package tech.cryptonomic.conseil.tezos.michelson.dto

/* Class representing a whole Michelson schema */
case class MichelsonSchema(parameter: MichelsonType, storage: MichelsonType, code: MichelsonCode)
