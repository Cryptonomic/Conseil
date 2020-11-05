package tech.cryptonomic.conseil.common.testkit

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.concurrent.ScalaFutures

/** Provides a commonly-used suite definition as word-spec with support for
  * logging, matchers, options, futures
  */
trait ConseilSpec
    extends AnyWordSpec
    with Matchers
    with LoggingTestSupport
    with OptionValues
    with EitherValues
    with ScalaFutures {}
