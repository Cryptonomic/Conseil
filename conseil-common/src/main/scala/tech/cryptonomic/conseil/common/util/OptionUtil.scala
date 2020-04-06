package tech.cryptonomic.conseil.common.util

/** Provide useful functions to work with Options */
object OptionUtil {

  /** Checks condition and returns the value wrapped in [[Some]], otherwise [[None]]
    *
    * @param condition verified to decide if the value should be returned
    * @param value returned when [[condition]] is satisfied
    * @return an optional value of [[T]]
    */
  def when[T](condition: Boolean)(value: => T): Option[T] = if (condition) Some(value) else None

  /** Same as [[when]] but the returned value is already optional.
    *
    * @param condition verified to decide if the value should be returned
    * @param optionalValue returned when [[condition]] is satisfied
    * @return an optional value of [[T]]
    */
  def whenOpt[T](condition: Boolean)(optionalValue: => Option[T]): Option[T] = if (condition) optionalValue else None
}
