package toguru.logging

import java.sql.{BatchUpdateException, SQLException}

import net.logstash.logback.marker.{LogstashMarker, Markers}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import org.slf4j.Logger
import scala.collection.JavaConverters._


object MockEventPublisher extends Events with MockitoSugar {
  override val eventLogger = mock[Logger]
}

class EventPublisherSpec extends WordSpec with Matchers {
  "Event Publisher" should {
    "handle java.sql.BatchUpdateExceptions correct" in {

      val sqlException1 = new SQLException("SQL Exception 1")
      val sqlException2 = new SQLException("SQL Exception 2")
      val sqlException3 = new SQLException("SQL Exception 3")

      val exception = new BatchUpdateException("BatchFailed", Array(3))


      exception.setNextException(sqlException1)
      exception.setNextException(sqlException2)
      exception.setNextException(sqlException3)

      MockEventPublisher.event("testEvent", exception, "toggleId" -> "someToggle")

      def markers: LogstashMarker =
        Markers.appendEntries(Map("toggleId" -> "someToggle", "exception_type" -> "java.sql.BatchUpdateException", "@name" -> "testEvent").asJava)

      verify(MockEventPublisher.eventLogger).error(markers, "BatchFailed, SQL Exception 1, SQL Exception 2, SQL Exception 3", exception)
    }

    "not fail if no messages are contained in chained exception" in {
      val exception = new BatchUpdateException("BatchFailed", Array(0))

      MockEventPublisher.event("testEvent", exception, "toggleId" -> "someToggle")

      def markers: LogstashMarker =
        Markers.appendEntries(Map("toggleId" -> "someToggle", "exception_type" -> "java.sql.BatchUpdateException", "@name" -> "testEvent").asJava)

      verify(MockEventPublisher.eventLogger).error(markers, "BatchFailed", exception)
    }
  }
}