package toguru.logging

import java.sql.BatchUpdateException

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

trait Event {

  def eventName: String

  def eventFields: Seq[(String, Any)]
}

trait EventPublishing {
  val publisher = EventPublisher
}

object EventPublisher {
  val eventLogger: Logger = LoggerFactory.getLogger("event-logger")

  def event(name: String, fields: (String, Any)*): Unit = eventLogger.info(markers(name, fields), "")

  def event(name: String, exception: Throwable, fields: (String, Any)*): Unit = {
    val eventMarkers = markers(name, fields :+ ("exception_type" -> exception.getClass.getName))
    exception match {
      case e: BatchUpdateException => eventLogger.error(eventMarkers, nestedExeceptionAsString(e), exception)
      case _ => eventLogger.error(eventMarkers, exception.getMessage, exception)
    }
  }

  def event(e: Event): Unit = event(e.eventName, e.eventFields: _*)

  def nestedExeceptionAsString(exception: BatchUpdateException): String = {
    val exceptions = exception.getMessage
    while(exception.getNextException != null) {
       exceptions + "\n\n" + exception.getNextException.getMessage
    }
    exceptions
  }

  private def markers(name: String, fields: Seq[(String, Any)]): LogstashMarker =
    appendEntries(Map(fields: _*).updated("@name", name).asJava)

}
