package toguru.helpers

import ch.qos.logback.classic.{Level, Logger}
import org.scalatest.{BeforeAndAfterAll, Suite}
import org.slf4j.LoggerFactory

trait DisabledLogging extends BeforeAndAfterAll { this: Suite =>

  override def beforeAll() = {
    val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    root.setLevel(Level.OFF)
    super.beforeAll()
  }
}
