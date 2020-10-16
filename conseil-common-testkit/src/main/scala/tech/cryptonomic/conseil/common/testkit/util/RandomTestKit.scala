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

/** A marker type for values that will not raise errors when stored inside a database.
  * Use this for arbitrary generators with proper built-in constraints that will prevent
  * db errors.
  *
  * @param value the actual value
  */
case class DBSafe[T](value: T) extends AnyVal

/** Common and generally useful random generation primitives, designed to be used for
  * test data creation.
  * We integrate this with [[org.scalacheck]] generation facilities, i.e. [[Gen]].
  * The advantage of using those is a useful number of combinators to build more complex
  * generators from simpler ones.
  */
trait RandomGenerationKit {

  private val nullUTF_8 = '\u0000'

  //a stable date time reference if needed
  lazy val testReferenceDateTime = LocalDate.of(2018, 1, 1).atStartOfDay(ZoneOffset.UTC)

  //a stable timestamp reference if needed
  lazy val testReferenceTimestamp =
    new Timestamp(testReferenceDateTime.toEpochSecond * 1000L)

  //creates pseudo-random strings of given length, based on an existing [[Random]] generator
  val alphaNumericGenerator = (random: Random) => random.alphanumeric.take(_: Int).mkString

  /** Generates random timestamps roughly between years 2015 and 2070 */
  lazy val timestampGenerator: Gen[Timestamp] = Gen.chooseNum[Long](1420115278L, 3155804811L).map(new Timestamp(_))

  /** Creates random instants roughly between years 2015 and 2070 */
  lazy val instantGenerator: Gen[Instant] = Gen.chooseNum[Long](1420115278L, 3155804811L).map(Instant.ofEpochMilli)

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

  /** Creates a generator for fixed-size strings where the characters come from the given alphabet
    *
    * @param size how long the resulting string is
    * @param alphabet valid chars included in the generated string, provided as a single string
    */
  def boundedAlphabetStringGenerator(size: Int, alphabet: String): Gen[String] =
    Gen.pick(size, alphabet.toSet).map(_.mkString)

  /** Generator for strings that can be safely used as column values in a db */
  val databaseFriendlyStringGenerator: Gen[DBSafe[String]] =
    arbitrary[String].map(string => DBSafe(string.filterNot(_ == nullUTF_8)))

  /** Use this to avoid generating BigDecimal values over a reasonable
    * precision to be stored as a database row entry.
    *
    * Example: we use this to avoid this postgres error:
    * "A field with precision 21, scale 2 must round to an absolute value less than 10^19"
    *
    * It might also be related to a JVM issue on the MathContext, as reported here:
    * https://stackoverflow.com/questions/27434061/mathcontexts-in-bigdecimals-scalacheck-generator-creates-bigdecimals-which-can
    */
  val databaseFriendlyBigDecimalGenerator: Gen[DBSafe[BigDecimal]] =
    arbitrary[Float]
      .map(BigDecimal.decimal)
      .retryUntil(canBeWrittenToDb)
      .map(DBSafe(_))

  /** Can the string be safely stored as a database column?
    * This depends on the database type definitions.
    */
  def canBeWrittenToDb(string: String): Boolean = !string.contains(nullUTF_8)

  /** Can the number be safely stored as a database column?
    * This depends on the database type definitions.
    */
  def canBeWrittenToDb(num: BigDecimal): Boolean = num.abs < 1e18
}
