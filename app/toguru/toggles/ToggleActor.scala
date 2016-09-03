package toguru.toggles

import akka.persistence.PersistentActor
import toguru.logging.EventPublishing
import akka.actor.{ActorRef, ActorSystem, Props}
import ToggleActor._

trait ToggleActorProvider {
  def create(id: String): ActorRef
  def stop(ref: ActorRef)
}

object ToggleActor {

  case class CreateToggleCommand(name: String, description: String, tags: Map[String, String])

  case class CreateSucceeded(id: String)

  case class ToggleAlreadyExists(id: String)

  case object GetToggle

  case class SetGlobalRolloutCommand(percentage: Int)

  case object DeleteGlobalRolloutCommand

  case object Success

  case class ToggleDoesNotExist(id: String)

  case class PersistFailed(id: String, cause: Throwable)

  def toId(name: String): String = name.trim.toLowerCase.replaceAll("\\s+", "-")

  def provider(system: ActorSystem) = new ToggleActorProvider {

    def create(id: String): ActorRef = system.actorOf(Props(new ToggleActor(id)))

    def stop(ref: ActorRef): Unit = system.stop(ref)
  }
}

class ToggleActor(toggleId: String, var maybeToggle: Option[Toggle] = None) extends PersistentActor with EventPublishing {

  val persistenceId = toggleId

  override def receiveRecover = {
    case ToggleCreated(name, description, tags) =>
      maybeToggle = Some(Toggle(toggleId, name, description, tags))

    case GlobalRolloutCreated(percentage) =>
      maybeToggle = maybeToggle.map { t => t.copy(rolloutPercentage = Some(percentage))}

    case GlobalRolloutUpdated(percentage) =>
      maybeToggle = maybeToggle.map { t => t.copy(rolloutPercentage = Some(percentage))}

    case GlobalRolloutDeleted() =>
      maybeToggle = maybeToggle.map { t => t.copy(rolloutPercentage = None) }
  }

  override def receiveCommand = handleToggleCommands.orElse(handleGlobalRolloutCommands)

  def handleToggleCommands: Receive = {
    case GetToggle => sender ! maybeToggle

    case CreateToggleCommand(name, description, tags) =>
      maybeToggle match {
        case Some(_) => sender ! ToggleAlreadyExists(toggleId)

        case None =>
          persist(ToggleCreated(name, description, tags)) { created =>
            receiveRecover(created)
            sender ! CreateSucceeded(toggleId)
          }
      }
  }

  def handleGlobalRolloutCommands: Receive = withExistingToggle {
    toggle => {
      case SetGlobalRolloutCommand(p) =>
        val event = if(toggle.rolloutPercentage.isDefined) GlobalRolloutUpdated(p) else GlobalRolloutCreated(p)
        persist(event) { event =>
          receiveRecover(event)
          sender ! Success
        }

      case DeleteGlobalRolloutCommand =>
        toggle.rolloutPercentage match {
          case Some(_) =>
            persist(GlobalRolloutDeleted()) { deleted =>
              receiveRecover(deleted)
              sender ! Success
            }
          case None => sender ! Success
        }
    }
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    sender ! PersistFailed(toggleId, cause)
  }

  def withExistingToggle(handler: Toggle => Receive): Receive = {
    case command if maybeToggle.isDefined => handler(maybeToggle.get)(command)
    case _                                => sender ! ToggleDoesNotExist(toggleId)
  }
}
