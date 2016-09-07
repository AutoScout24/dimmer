package toguru.toggles

import javax.inject.Inject

import akka.actor.{Actor, ActorContext, ActorRef}
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import ToggleStateActor._
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal

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

class ToggleStateActor(startHook: (ActorContext, ActorRef) => Unit, var toggles: Map[String, ToggleState] = Map.empty) extends Actor {

  @Inject()
  def this(readJournal: JdbcReadJournal) = this(sendToggleEvents(readJournal))

  override def preStart() = startHook(context, self)

  override def receive = {
    case GetState                     => sender ! toggles
    case (id: String, e: ToggleEvent) => handleEvent(id, e)
    case Shutdown                     => context.stop(self)
  }

  def handleEvent(id: String, event: ToggleEvent) = event match {
    case ToggleCreated(_, _, tags, _) => toggles = toggles.updated(id, ToggleState(id, tags, None))
    case GlobalRolloutCreated(p, _)   => update(id, _.copy(rolloutPercentage = Some(p)))
    case GlobalRolloutUpdated(p, _)   => update(id, _.copy(rolloutPercentage = Some(p)))
    case GlobalRolloutDeleted(_)      => update(id, _.copy(rolloutPercentage = None))
  }

  def update(id: String, fn: ToggleState => ToggleState): Unit =
    toggles.get(id).map(fn).foreach(s => toggles = toggles.updated(s.id, s))
}
