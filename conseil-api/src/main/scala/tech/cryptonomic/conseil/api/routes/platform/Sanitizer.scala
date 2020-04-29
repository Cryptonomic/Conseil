package tech.cryptonomic.conseil.api.routes.platform

object Sanitizer {

  /** Sanitizes string to be viable to paste into plain SQL */
  def sanitizeForSql(str: String): String = {
    val supportedCharacters = Set('_', '.', '+', ':', '-', ' ', '%', '"', '(', ')')
    str.filter(c => c.isLetterOrDigit || supportedCharacters.contains(c)).flatMap {
      case '%' => """\%"""
      case c => c.toString
    }
  }

  /** Sanitizes datePart aggregate function*/
  def sanitizeDatePartAggregation(str: String): String = {
    val supportedCharacters = Set('Y', 'M', 'D', 'A', '-')
    str.filterNot(c => c.isWhitespace || !supportedCharacters.contains(c))
  }

}
