package toguru.logging

import java.sql.{BatchUpdateException, SQLException}

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

trait Events {
  val eventLogger: Logger

  def event(name: String, fields: (String, Any)*): Unit = eventLogger.info(markers(name, fields), "")

  def event(name: String, exception: Throwable, fields: (String, Any)*): Unit = {
    val eventMarkers = markers(name, fields :+ ("exception_type" -> exception.getClass.getName))
    val message = exception match {
      case e: SQLException => nestedExceptionAsString(e).mkString(", ")
      case _ => exception.getMessage
    }
    eventLogger.error(eventMarkers, message, exception)
  }

  def event(e: Event): Unit = event(e.eventName, e.eventFields: _*)

  def nestedExceptionAsString(exception: SQLException): Seq[String] =
      exception.asScala.map(_.getMessage).toSeq

  private def markers(name: String, fields: Seq[(String, Any)]): LogstashMarker =
    appendEntries(Map(fields: _*).updated("@name", name).asJava)
}

object EventPublisher extends Events {
  val eventLogger = LoggerFactory.getLogger("event-logger")
}
