package tech.cryptonomic.conseil.common.testkit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import scribe._
import scribe.format.Formatter
import scribe.modify.LevelFilter

/** mix-in this in a test-suite to get properly configured loggers in the test-run */
trait LoggingTestSupport extends BeforeAndAfterAll {
  suite: Suite =>

  /** Any test can override this level to enable specific logging. */
  protected val enabledLogLevel: Option[Level] = None

  override protected def beforeAll(): Unit = {
    //we reset the level before preparing the test, to reduce noise even more
    info(s"setting minimum level to ${enabledLogLevel.getOrElse("off")}")
    enabledLogLevel match {
      case None =>
        Logger.root
          .clearModifiers()
          .withHandler(modifiers = List(LevelFilter.ExcludeAll))
          .clearHandlers()
          .replace()
      case Some(level) =>
        Logger.root
          .withMinimumLevel(level)
          .withHandler(formatter = Formatter.simple)
          .replace()
    }
    super.beforeAll()
  }
}
