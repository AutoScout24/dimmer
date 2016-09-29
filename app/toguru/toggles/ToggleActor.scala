package toguru.toggles

import akka.persistence.PersistentActor
import toguru.logging.EventPublishing
import akka.actor.{ActorRef, ActorSystem, Props}
import ToggleActor._
import toguru.events.toggles._

trait ToggleActorProvider {
  def create(id: String): ActorRef
  def stop(ref: ActorRef)
}

object ToggleActor {

  case class CreateToggleCommand(name: String, description: String, tags: Map[String, String])

  case class CreateSucceeded(id: String)

  case class ToggleAlreadyExists(id: String)

  case class UpdateToggleCommand(name: Option[String], description: Option[String], tags: Option[Map[String, String]])

  case class DeleteToggleCommand(delete: Option[Boolean])

  case object UnconfirmedDelete

  case object GetToggle

  case class SetGlobalRolloutCommand(percentage: Int)

  case object DeleteGlobalRolloutCommand

  case object Success

  case class ToggleDoesNotExist(id: String)

  case class PersistFailed(id: String, cause: Throwable)

  case object Shutdown

  def toId(name: String): String = name.trim.toLowerCase.replaceAll("\\s+", "-")

  def provider(system: ActorSystem) = new ToggleActorProvider {

    def create(id: String): ActorRef = system.actorOf(Props(new ToggleActor(id)))

    def stop(ref: ActorRef): Unit = ref ! Shutdown
  }
}

class ToggleActor(toggleId: String, var maybeToggle: Option[Toggle] = None) extends PersistentActor with EventPublishing {

  val persistenceId = toggleId

  override def receiveRecover = {
    case ToggleCreated(name, description, tags, _) =>
      maybeToggle = Some(Toggle(toggleId, name, description, tags))

    case ToggleUpdated(name, description, tags, _) =>
      maybeToggle = maybeToggle.map(_.copy(name = name, description = description, tags = tags))

    case ToggleDeleted(_) =>
      maybeToggle = None

    case GlobalRolloutCreated(p, _) =>
      maybeToggle = maybeToggle.map(_.copy(rolloutPercentage = Some(p)))

    case GlobalRolloutUpdated(p, _) =>
      maybeToggle = maybeToggle.map(_.copy(rolloutPercentage = Some(p)))

    case GlobalRolloutDeleted(_) =>
      maybeToggle = maybeToggle.map(_.copy(rolloutPercentage = None))
  }

  override def receiveCommand = handleToggleCommands.orElse(
    withExistingToggle(t => handleToggleModificationCommands(t).orElse(handleGlobalRolloutCommands(t))))

  def handleToggleCommands: Receive = {
    case Shutdown => context.stop(self)

    case GetToggle => sender ! maybeToggle

    case CreateToggleCommand(name, description, tags) =>
      maybeToggle match {
        case Some(_) => sender ! ToggleAlreadyExists(toggleId)

        case None =>
          persist(ToggleCreated(name, description, tags, meta)) { created =>
            receiveRecover(created)
            sender ! CreateSucceeded(toggleId)
          }
      }
  }

  def handleToggleModificationCommands(t: Toggle): Receive = {
    case UpdateToggleCommand(name, description, tags) =>
      val updated = ToggleUpdated(
        name.getOrElse(t.name), description.getOrElse(t.description), tags.getOrElse(t.tags), meta)
      persist(updated) { updated =>
        receiveRecover(updated)
        sender ! Success
      }

    case DeleteToggleCommand(Some(true)) =>
      persist(ToggleDeleted(meta)) { deleted =>
        receiveRecover(deleted)
        sender ! Success
    }

    case DeleteToggleCommand(_) => sender ! UnconfirmedDelete
  }

  def handleGlobalRolloutCommands(toggle: Toggle): Receive =  {
    case SetGlobalRolloutCommand(p) =>
      val event = if(toggle.rolloutPercentage.isDefined) GlobalRolloutUpdated(p, meta) else GlobalRolloutCreated(p, meta)
      persist(event) { event =>
        receiveRecover(event)
        sender ! Success
      }

    case DeleteGlobalRolloutCommand =>
      toggle.rolloutPercentage match {
        case Some(_) =>
          persist(GlobalRolloutDeleted(meta)) { deleted =>
            receiveRecover(deleted)
            sender ! Success
          }
        case None => sender ! Success
      }
  }

  def meta = Some(Metadata(time, ""))

  def time = System.currentTimeMillis

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long) = {
    publisher.event("toggle-persist-failed", cause, "toggleId" -> persistenceId, "eventType" -> event.getClass.getSimpleName)
    sender ! PersistFailed(toggleId, cause)
  }

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long) = {
    publisher.event("toggle-persist-rejected", cause, "toggleId" -> persistenceId, "eventType" -> event.getClass.getSimpleName)
    sender ! PersistFailed(toggleId, cause)
  }

  def withExistingToggle(handler: Toggle => Receive): Receive = {
    case command if maybeToggle.isDefined => handler(maybeToggle.get)(command)
    case _                                => sender ! ToggleDoesNotExist(toggleId)
  }
}
