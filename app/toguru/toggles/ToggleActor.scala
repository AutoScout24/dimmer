package toguru.toggles

import akka.persistence.PersistentActor
import toguru.logging.EventPublishing
import akka.actor.{ActorRef, ActorSystem, Props}
import ToggleActor._
import akka.event.slf4j.Logger

trait ToggleActorProvider {
  def create(id: String): ActorRef
  def stop(ref: ActorRef)
}

object ToggleActor {
  case class CreateToggleCommand(name: String, description: String, tags: Map[String, String])

  case class CreateGlobalRolloutConditionCommand(percentage: Int)

  case class UpdateGlobalRolloutConditionCommand(percentage: Int)

  case class CreateSucceeded(id: String)

  case class PersistFailed(id: String, cause: Throwable)

  case class ToggleAlreadyExists(id: String)

  case class ToggleDoesNotExist(id: String)

  case class GlobalRolloutConditionDoesNotExist(id: String)

  case object Success

  case object GetToggle

  def toId(name: String): String = name.trim.toLowerCase.replaceAll("\\s+", "-")

  def provider(system: ActorSystem) = new ToggleActorProvider {

    def create(id: String): ActorRef = system.actorOf(Props(new ToggleActor(id)))

    def stop(ref: ActorRef): Unit = system.stop(ref)
  }
}

class ToggleActor(toggleId: String, var toggle: Option[Toggle] = None) extends PersistentActor with EventPublishing {

  val persistenceId = toggleId

  override def receiveRecover: Receive = {
    case ToggleCreated(name, description, tags) =>
      toggle = Some(Toggle(toggleId, name, description, tags))
    case GlobalRolloutCreated(percentage) =>
      toggle = toggle.map{ t => t.copy(rolloutPercentage = Some(percentage))}
    case GlobalRolloutUpdated(percentage) =>
      toggle = toggle.map{ t => t.copy(rolloutPercentage = Some(percentage))}
  }

  override def receiveCommand: Receive = {
    case CreateToggleCommand(name, description, tags) =>
      toggle match {
        case Some(_) => sender ! ToggleAlreadyExists(toggleId)
        case None    => persistCreateEvent(name, description, tags)
      }

    case GetToggle => sender ! toggle

    case UpdateGlobalRolloutConditionCommand(percentage) =>
     withExistingToggle { t =>
          t.rolloutPercentage match {
            case Some(p) => persist(GlobalRolloutUpdated(percentage)) { set =>
              receiveRecover(set)
              sender ! Success
            }
            case None => sender ! GlobalRolloutConditionDoesNotExist(toggleId)
          }
      }

    case CreateGlobalRolloutConditionCommand(percentage) =>
      withExistingToggle { t =>
        persist(GlobalRolloutCreated(percentage)) { set =>
          receiveRecover(set)
          sender ! Success
        }
      }
  }

  def persistCreateEvent(name: String, description: String, tags: Map[String, String]): Unit = {
    persist(ToggleCreated(name, description, tags)) { created =>
      receiveRecover(created)
      sender ! CreateSucceeded(toggleId)
    }
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    sender ! PersistFailed(toggleId, cause)
  }

  def withExistingToggle(handler: Toggle => Unit): Unit = {
    toggle match {
      case Some(t) => handler(t)
      case None    =>  sender ! ToggleDoesNotExist(toggleId)
    }
  }
}
