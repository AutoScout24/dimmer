package toguru.toggles

import javax.inject.Inject

import akka.actor.{Actor, ActorContext, ActorRef}
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import ToggleStateActor._
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import toguru.logging.EventPublishing

object ToggleStateActor {
  case object Shutdown
  case object GetState

  def sendToggleEvents(readJournal: JdbcReadJournal): (ActorContext, ActorRef) => Unit = { (context, self) =>
    implicit val mat: Materializer = ActorMaterializer()(context.system)
    readJournal.eventsByTag("toggle", 0L).map(env => (env.persistenceId, env.event)).runWith(Sink.actorRef(self, Shutdown))
  }
}

case class ToggleState(id: String,
                       tags: Map[String, String] = Map.empty,
                       rolloutPercentage: Option[Int] = None)

class ToggleStateActor(startHook: (ActorContext, ActorRef) => Unit, var toggles: Map[String, ToggleState] = Map.empty)
  extends Actor with EventPublishing {

  @Inject()
  def this(readJournal: JdbcReadJournal) = this(sendToggleEvents(readJournal))

  override def preStart() = {
    startHook(context, self)
    publisher.event("state-actor-started")
  }

  override def postStop() = publisher.event("state-actor-stopped")

  override def receive = {
    case GetState =>
      publisher.event("state-actor-get-request", "togglesCount" -> toggles.values.size)
      sender ! toggles

    case (id: String, e: ToggleEvent) =>
      publisher.event("state-actor-toggle-event", "eventType" -> e.getClass.getSimpleName, "readJournalLatencyMs" -> latency(e.meta))
      handleEvent(id, e)

    case Shutdown => context.stop(self)
  }

  def latency(meta: Option[Metadata]): Long = meta.map(m => System.currentTimeMillis - m.time).getOrElse(0)

  def handleEvent(id: String, event: ToggleEvent) = event match {
    case ToggleCreated(_, _, tags, _) => toggles = toggles.updated(id, ToggleState(id, tags, None))
    case GlobalRolloutCreated(p, _)   => updateToggle(id, _.copy(rolloutPercentage = Some(p)))
    case GlobalRolloutUpdated(p, _)   => updateToggle(id, _.copy(rolloutPercentage = Some(p)))
    case GlobalRolloutDeleted(_)      => updateToggle(id, _.copy(rolloutPercentage = None))
  }

  def updateToggle(id: String, update: ToggleState => ToggleState): Unit =
    toggles.get(id).map(update).foreach(t => toggles = toggles.updated(id, t))
}
