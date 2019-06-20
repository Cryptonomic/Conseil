package tech.cryptonomic.conseil.util

import java.sql.Timestamp
import java.time.{LocalDate, ZoneOffset}
import scala.util.Random

/* use this to make random generation implicit but deterministic */
case class RandomSeed(seed: Long) extends AnyVal with Product with Serializable {
  def +(extra: Long): RandomSeed = RandomSeed(seed + extra)
}

trait RandomGenerationKit {

  //a stable date time reference if needed
  lazy val testReferenceDateTime = LocalDate.of(2018, 1, 1).atStartOfDay(ZoneOffset.UTC)

  //a stable timestamp reference if needed
  lazy val testReferenceTimestamp =
    new Timestamp(testReferenceDateTime.toEpochSecond)

  //creates pseudo-random strings of given length, based on an existing [[Random]] generator
  val alphaNumericGenerator = (random: Random) => random.alphanumeric.take(_: Int).mkString
}
