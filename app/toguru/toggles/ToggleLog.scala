package toguru.toggles

import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Sink

object ToggleLog {

  def sendToggleEvents(readJournal: JdbcReadJournal, endOfStreamMessage: Any): ActorInitializer = { (context, actor) =>
    implicit val mat: Materializer = ActorMaterializer()(context.system)
    readJournal.eventsByTag("toggle", 0L).runWith(Sink.actorRef(actor, endOfStreamMessage))
  }
}
