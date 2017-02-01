package toguru.toggles

import javax.inject.Inject

import akka.actor.{Actor, Cancellable, Scheduler}
import ToggleStateActor._
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.EventEnvelope
import slick.backend.DatabaseConfig
import slick.driver.JdbcDriver
import toguru.helpers.FutureTimeout.timeout
import toguru.logging.EventPublishing
import toguru.toggles.events._

import scala.concurrent.duration._
import scala.concurrent._

object ToggleState {

  case class Config(initialize: Boolean = false)

}

object ToggleStateActor {
  case object Shutdown
  case object GetState
  case object GetSeqNo
  case object ToggleStateInitializing
  case object CheckSequenceNo
  case class DbSequenceNo(sequenceNo: Long)

  type SequenceNoProvider = (Scheduler, ExecutionContext) => Future[Long]

  def dbSequenceNoProvider(dbConfig: DatabaseConfig[JdbcDriver]): SequenceNoProvider = { (scheduler: Scheduler, executionContext: ExecutionContext) =>
    import dbConfig.driver.api._

    implicit val ec = executionContext
    implicit val s = scheduler

    timeout(500.millis, dbConfig.db.run(sql"""SELECT MAX(ordering) FROM journal""".as[Long].head))
  }
}

case class ToggleState(id: String,
                       tags: Map[String, String] = Map.empty,
                       rolloutPercentage: Option[Int] = None)

case class ToggleStates(sequenceNo: Long, toggles: Seq[ToggleState])

class ToggleStateActor(
                        startHook: ActorInitializer,
                        sequenceNoProvider: SequenceNoProvider,
                        val config: ToggleState.Config = ToggleState.Config(),
                        var toggles: Map[String, ToggleState] = Map.empty,
                        var lastSequenceNo: Long = 0)
  extends Actor with EventPublishing {

  @Inject()
  def this(readJournal: JdbcReadJournal, dbConfig: DatabaseConfig[JdbcDriver], config: ToggleState.Config) =
    this(
      ToggleLog.sendToggleEvents(readJournal, Shutdown),
      dbSequenceNoProvider(dbConfig),
      config)

  var fetchDbSequenceNo: Cancellable = _

  override def preStart() = {
    startHook(context, self)
    publisher.event("state-actor-started")
    implicit val ec = context.dispatcher
    fetchDbSequenceNo = context.system.scheduler.schedule(1.seconds, 3.seconds, self, CheckSequenceNo)
  }

  override def postStop() = {
    fetchDbSequenceNo.cancel()
    publisher.event("state-actor-stopped")
  }

  override def receive = if (config.initialize) initializing.orElse(global) else initialized.orElse(global)

  def global: Receive = {
    case EventEnvelope(offset, id, _, e: ToggleEvent) => handleEvent(id, e, offset)

    case Shutdown => context.stop(self)
  }

  def initializing: Receive = {
    case GetState => sender ! ToggleStateInitializing

    case GetSeqNo => sender ! ToggleStateInitializing

    case CheckSequenceNo =>
      implicit val ec = context.dispatcher
      sequenceNoProvider(context.system.scheduler, context.dispatcher).map(seqNo => self ! DbSequenceNo(seqNo))

    case DbSequenceNo(seqNo) =>
      if(seqNo == lastSequenceNo) {
        fetchDbSequenceNo.cancel()
        context.become(initialized.orElse(global))
      }
  }

  def initialized: Receive = {
    case GetState => sender ! ToggleStates(lastSequenceNo, toggles.values.to[Vector].sortBy(_.id))

    case GetSeqNo => sender ! lastSequenceNo
  }

  def latency(meta: Option[Metadata]): Long = meta.map(m => System.currentTimeMillis - m.time).getOrElse(0)

  def handleEvent(id: String, event: ToggleEvent, offset: Long) = {
    publisher.event("state-actor-toggle-event", "eventType" -> event.getClass.getSimpleName, "readJournalLatencyMs" -> latency(event.meta))
    event match {
      case ToggleCreated(_, _, tags, _) => toggles = toggles.updated(id, ToggleState(id, tags, None))
      case ToggleUpdated(_, _, tags, _) => updateToggle(id, _.copy(tags = tags))
      case ToggleDeleted(_)             => toggles = toggles - id
      case GlobalRolloutCreated(p, _)   => updateToggle(id, _.copy(rolloutPercentage = Some(p)))
      case GlobalRolloutUpdated(p, _)   => updateToggle(id, _.copy(rolloutPercentage = Some(p)))
      case GlobalRolloutDeleted(_)      => updateToggle(id, _.copy(rolloutPercentage = None))
    }
    lastSequenceNo = offset
  }

  def updateToggle(id: String, update: ToggleState => ToggleState): Unit =
    toggles.get(id).map(update).foreach(t => toggles = toggles.updated(id, t))
}
