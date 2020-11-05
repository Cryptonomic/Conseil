package tech.cryptonomic.conseil.common.testkit

import org.scalatest.BeforeAndAfterAll
import wvlet.log.Logger
import org.scalatest.Suite

/** mix-in this in a test-suite to get properly configured loggers in the test-run
  * This will correctly scan the properties file from the test classpath too.
  */
trait LoggingTestSupport extends BeforeAndAfterAll {
  suite: Suite =>

  override protected def beforeAll(): Unit = {
    Logger.scheduleLogLevelScan
    super.beforeAll()
  }
}
