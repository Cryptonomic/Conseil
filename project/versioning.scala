object Versioning {

  def zeroPadded(upto: Int) =
    (s: String) => (("0" * upto) ++ s.take(upto)) takeRight upto

  /* implement the logic for versioning explained in the build file */
  def generate(major: Int, date: java.time.LocalDate, tag: String): String = {
    val month = zeroPadded(2)(date.getMonthValue.toString)
    val year = date.getYear.toString.takeRight(2)
    val paddedTag = zeroPadded(4)(tag.filter(_.isDigit))
    s"$major.$year$month.$paddedTag"
  }
}