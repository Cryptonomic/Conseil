package tech.cryptonomic.conseil.util

object OptionUtil {
  def when[T](condition: Boolean)(value: => T): Option[T] = if (condition) Some(value) else None
}
