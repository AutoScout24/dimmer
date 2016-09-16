package toguru.toggles

import javax.inject.Inject

import akka.actor.{Actor, ActorContext, ActorRef}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import toguru.logging.EventPublishing
import toguru.events.toggles._
import AuditLogActor._

object AuditLogActor {
  case object GetLog
  case object Shutdown
}

class AuditLogActor(startHook: (ActorContext, ActorRef) => Unit, var log: Seq[(String, ToggleEvent)] = List.empty)
  extends Actor with EventPublishing {

  @Inject()
  def this(readJournal: JdbcReadJournal) = this(ToggleLog.sendToggleEvents(readJournal, Shutdown))

  override def preStart() = {
    startHook(context, self)
    publisher.event("state-actor-started")
  }

  override def postStop() = publisher.event("state-actor-stopped")

  override def receive = {
    case GetLog =>
      publisher.event("audit-actor-get-request", "logSize" -> log.size)
      sender ! log

    case (id: String, e: ToggleEvent) =>
      publisher.event("audit-actor-toggle-event", "eventType" -> e.getClass.getSimpleName, "readJournalLatencyMs" -> latency(e.meta))
      log = (id, e) +: log

    case Shutdown => context.stop(self)
  }

  def latency(meta: Option[Metadata]): Long = meta.map(m => System.currentTimeMillis - m.time).getOrElse(0)
}
