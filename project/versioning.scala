object Versioning {

  import java.time._

  def zeroPadded(upto: Int) =
    (s: String) => (("0" * upto) ++ s.take(upto)) takeRight upto

  /* implement the logic for versioning explained in the build file */
  def generate(major: Int, date: LocalDate, tag: String): String = {
    val week = zeroPadded(2)(date.get(temporal.ChronoField.ALIGNED_WEEK_OF_YEAR).toString)
    val year = date.getYear.toString.takeRight(2)
    val nextTagVersion = tag.filter(_.isDigit).toInt + 1
    val paddedTagVersion = zeroPadded(4)(String.valueOf(nextTagVersion))
    s"$major.$year$week.$paddedTagVersion"
  }

  /* val tagRelease: Task[...]
   *
   * The main idea is to create a task that
   *  1. reads the current version in the format created by the generate method
   *  2. extracts the relevant tag counter, that is, the <n> in "major.yyww.<n>"
   *  3. remove the leading zeroes and make a new tag:
   *     `git tag -a -m "should we set a message here or read from local ci file?" ci-release-<n>`
   *     The actual message might point to some file or else that could identify the context for future reference
   *  4. commit the tag
   *
   * Once tagged, the new version will be picked up by sbt-git.
   *
   * So CI should simply do a sequence like: sbt clean test [it/test] publishSigned publishRelease
   * Then run this task to update the tag to latest...
   */
}