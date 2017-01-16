package toguru.toggles

import javax.inject.Inject

import akka.actor.{Actor, ActorContext, ActorRef, Cancellable}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import toguru.logging.EventPublishing
import toguru.toggles.events._
import AuditLog.Entry
import AuditLogActor._

import scala.concurrent.duration._


object AuditLog {
  case class Config(retentionTime: Duration = 90.days, retentionLength: Int = 10000)

  case class Entry(id: String, event: ToggleEvent) {
    def laterThan(deadline: Long): Boolean = event.meta.exists(_.time > deadline)
  }
}

object AuditLogActor {
  case object GetLog
  case object Cleanup
  case object Shutdown
}

class AuditLogActor(
        startHook: (ActorContext, ActorRef) => Unit,
        val time: () => Long,
        val retentionTime: Duration,
        val retentionLength: Int,
        var log: Seq[Entry] = List.empty)
  extends Actor with EventPublishing {

  var cleanupTask: Cancellable = _

  @Inject()
  def this(readJournal: JdbcReadJournal, config: AuditLog.Config) =
    this(ToggleLog.sendToggleEvents(readJournal, Shutdown), System.currentTimeMillis, config.retentionTime, config.retentionLength)

  override def preStart() = {
    startHook(context, self)
    cleanupTask = context.system.scheduler.schedule(10.seconds, 30.minutes, context.self, Cleanup)(context.dispatcher)
    publisher.event("audit-actor-started")
  }

  override def postStop() = {
    cleanupTask.cancel()
    publisher.event("audit-actor-stopped")
  }

  override def receive = {
    case GetLog =>
      publisher.event("audit-actor-get-request", "logSize" -> log.size)
      sender ! log

    case (id: String, e: ToggleEvent) =>
      publisher.event("audit-actor-toggle-event", "eventType" -> e.getClass.getSimpleName, "readJournalLatencyMs" -> latency(e.meta))
      log = Entry(id, e) +: log.take(retentionLength - 1)

    case Cleanup =>
      val currentDeadline = deadline()
      log = log.filter(_.laterThan(currentDeadline))

    case Shutdown => context.stop(self)
  }

  def latency(meta: Option[Metadata]): Long = meta.map(m => System.currentTimeMillis - m.time).getOrElse(0)

  def deadline(): Long = time() - retentionTime.toMillis
}
