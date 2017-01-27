package toguru.toggles

import javax.inject.Inject

import akka.actor.{Actor, ActorContext, ActorRef}
import ToggleStateActor._
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.EventEnvelope
import toguru.logging.EventPublishing
import toguru.toggles.events._

object ToggleStateActor {
  case object Shutdown
  case object GetState

}

case class ToggleState(id: String,
                       tags: Map[String, String] = Map.empty,
                       rolloutPercentage: Option[Int] = None)

case class ToggleStates(sequenceNo: Long, toggles: Seq[ToggleState])

class ToggleStateActor(startHook: ActorInitializer, var toggles: Map[String, ToggleState] = Map.empty, var lastSequenceNo: Long = 0)
  extends Actor with EventPublishing {

  @Inject()
  def this(readJournal: JdbcReadJournal) = this(ToggleLog.sendToggleEvents(readJournal, Shutdown))

  override def preStart() = {
    startHook(context, self)
    publisher.event("state-actor-started")
  }

  override def postStop() = publisher.event("state-actor-stopped")

  override def receive = {
    case GetState =>
      publisher.event("state-actor-get-request", "togglesCount" -> toggles.values.size)
      sender ! ToggleStates(lastSequenceNo, toggles.values.to[Vector].sortBy(_.id))

    case EventEnvelope(offset, id, _, e: ToggleEvent) =>
      publisher.event("state-actor-toggle-event", "eventType" -> e.getClass.getSimpleName, "readJournalLatencyMs" -> latency(e.meta))
      handleEvent(id, e)
      lastSequenceNo = offset

    case Shutdown => context.stop(self)
  }

  def latency(meta: Option[Metadata]): Long = meta.map(m => System.currentTimeMillis - m.time).getOrElse(0)

  def handleEvent(id: String, event: ToggleEvent) = event match {
    case ToggleCreated(_, _, tags, _) => toggles = toggles.updated(id, ToggleState(id, tags, None))
    case ToggleUpdated(_, _, tags, _) => updateToggle(id, _.copy(tags = tags))
    case ToggleDeleted(_)             => toggles = toggles - id
    case GlobalRolloutCreated(p, _)   => updateToggle(id, _.copy(rolloutPercentage = Some(p)))
    case GlobalRolloutUpdated(p, _)   => updateToggle(id, _.copy(rolloutPercentage = Some(p)))
    case GlobalRolloutDeleted(_)      => updateToggle(id, _.copy(rolloutPercentage = None))
  }

  def updateToggle(id: String, update: ToggleState => ToggleState): Unit =
    toggles.get(id).map(update).foreach(t => toggles = toggles.updated(id, t))
}
