package toguru.toggles

import akka.actor.{ActorContext, ActorRef}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Sink

object ToggleLog {

  def sendToggleEvents(readJournal: JdbcReadJournal, shutdown: Any): (ActorContext, ActorRef) => Unit = { (context, self) =>
    implicit val mat: Materializer = ActorMaterializer()(context.system)
    readJournal.eventsByTag("toggle", 0L).map(env => (env.persistenceId, env.event)).runWith(Sink.actorRef(self, shutdown))
  }
}
