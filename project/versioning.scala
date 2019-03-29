/** defines utilities to calculate the versioning values */
object Versioning {

  import java.time._
  import sbt._
  import sbt.Keys._

  private def zeroPadded(upto: Int) =
    (s: String) => (("0" * upto) ++ s.take(upto)) takeRight upto

  //currently allows "-SNAPSHOT" as a valid suffix for a release
  val releasePattern = raw"^ci-release-(\d+)(-.*)?".r

  /**
    * implement the logic for versioning explained in the build file
    * the tag parameter should have a format like:
    *   ci-release-1-3-d3a9863 <-- this latter part is the result of `git describe`
    *              ^
    *              |- this is the version we care about
    */
  def generate(major: Int, date: LocalDate, tag: String): String = {
    val week = zeroPadded(2)(date.get(temporal.ChronoField.ALIGNED_WEEK_OF_YEAR).toString)
    val year = date.getYear.toString.takeRight(2)
    val nextTagVersion = releasePattern.findAllIn(tag).group(1).toInt + 1
    val paddedTagVersion = zeroPadded(4)(String.valueOf(nextTagVersion))
    s"$major.$year$week.$paddedTagVersion"
  }

  /**
    * Reads the current version in the format created by the generate method
    * Then extracts the relevant tag counter, that is, the <n> in "major.yyww.<n>",
    * removes the leading zeroes and gives back the potential new release tag
    */
  lazy val prepareReleaseTagDef = Def.task {
    val currentVersion = version.value
    val tag = currentVersion.split('.').last.dropWhile(_ == '0')
    s"ci-release-$tag"
  }

}
