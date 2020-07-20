package tech.cryptonomic.conseil.common.testkit.util

import java.sql.Timestamp
import java.time.{Instant, LocalDate, ZoneOffset}

import scala.util.Random
import org.scalacheck.Gen
import org.scalacheck.Arbitrary.arbitrary
import java.time.ZonedDateTime

/* use this to make random generation implicit but deterministic */
case class RandomSeed(seed: Long) extends AnyVal with Product with Serializable {
  def +(extra: Long): RandomSeed = RandomSeed(seed + extra)
}

/** Common and generally useful random generation primitives, designed to be used for
  * test data creation.
  * We integrate this with [[org.scalacheck]] generation facilities, i.e. [[Gen]].
  * The advantage of using those is a useful number of combinators to build more complex
  * generators from simpler ones.
  */
trait RandomGenerationKit {

  //a stable date time reference if needed
  lazy val testReferenceDateTime = LocalDate.of(2018, 1, 1).atStartOfDay(ZoneOffset.UTC)

  //a stable timestamp reference if needed
  lazy val testReferenceTimestamp =
    new Timestamp(testReferenceDateTime.toEpochSecond)

  //creates pseudo-random strings of given length, based on an existing [[Random]] generator
  val alphaNumericGenerator = (random: Random) => random.alphanumeric.take(_: Int).mkString

  /** Generates random timestamps */
  lazy val timestampGenerator: Gen[Timestamp] = Gen.posNum[Long].map(new Timestamp(_))

  /** Creates random instants */
  lazy val instantGenerator: Gen[Instant] = Gen.calendar.map(cal => Instant.ofEpochMilli(cal.getTimeInMillis()))

  /** Creates random date times with UTC Zone */
  lazy val utcZoneDateTimeGen: Gen[ZonedDateTime] = instantGenerator.map(
    ZonedDateTime.ofInstant(_, ZoneOffset.UTC)
  )

  /** Creates a generator of alphanumeric strings of arbitrary length */
  val alphaNumericUnsizedGenerator: Gen[String] = Gen.alphaNumStr

  /** Creates a generator of alphanumeric strings of given length */
  val alphaNumericSizedGenerator: Int => Gen[String] = length =>
    alphaNumericUnsizedGenerator.retryUntil(_.length == length)

  /** Creates a generator for strings where the characters come from the given alphabet
    *
    * @param alphabet valid chars included in the generated string, provided as a single string
    */
  def boundedAlphabetStringGenerator(alphabet: String): Gen[String] =
    Gen.someOf(alphabet.toSet).map(_.mkString)

  /** Use this to avoid generating BigDecimal values over a reasonable
    * precision to be stored as a database row entry.
    *
    * Example: we use this to avoid this postgres error:
    * "A field with precision 21, scale 2 must round to an absolute value less than 10^19"
    *
    * It might also be related to a JVM issue on the MathContext, as reported here:
    * https://stackoverflow.com/questions/27434061/mathcontexts-in-bigdecimals-scalacheck-generator-creates-bigdecimals-which-can
    */
  val databaseFriendlyBigDecimalGenerator: Gen[BigDecimal] =
    arbitrary[Float].map(BigDecimal.decimal).retryUntil(_.abs < 1e18)

}
