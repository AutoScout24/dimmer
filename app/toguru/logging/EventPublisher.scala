package toguru.logging

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers.appendEntries
import org.slf4j.{Logger, LoggerFactory}

trait Event {

  def eventName: String

  def eventFields: Seq[(String, Any)]
}

trait EventPublishing {
  val publisher = EventPublisher
}

object EventPublisher {
  val eventLogger: Logger = LoggerFactory.getLogger("event-logger")

  import collection.JavaConverters._

  def event(name: String, fields: (String, Any)*): Unit = eventLogger.info(markers(name, fields), "")

  def event(name: String, exception: Throwable, fields: (String, Any)*): Unit = {
    val eventMarkers = markers(name, fields :+ ("exception_type" -> exception.getClass.getName))

    eventLogger.info(eventMarkers, exception.getMessage, exception)
  }

  def event(e: Event): Unit = event(e.eventName, e.eventFields: _*)

  private def markers(name: String, fields: Seq[(String, Any)]): LogstashMarker =
    appendEntries(Map(fields: _*).updated("@name", name).asJava)

}
