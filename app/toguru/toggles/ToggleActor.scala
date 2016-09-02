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

  case object Success

  case class ToggleDoesNotExist(id: String)

  case class PersistFailed(id: String, cause: Throwable)

  def toId(name: String): String = name.trim.toLowerCase.replaceAll("\\s+", "-")

  def provider(system: ActorSystem) = new ToggleActorProvider {

    def create(id: String): ActorRef = system.actorOf(Props(new ToggleActor(id)))

    def stop(ref: ActorRef): Unit = system.stop(ref)
  }
}

class ToggleActor(toggleId: String, var toggle: Option[Toggle] = None) extends PersistentActor with EventPublishing {

  val persistenceId = toggleId

  override def receiveRecover = {
    case ToggleCreated(name, description, tags) =>
      toggle = Some(Toggle(toggleId, name, description, tags))

    case GlobalRolloutCreated(percentage) =>
      toggle = toggle.map{ t => t.copy(rolloutPercentage = Some(percentage))}

    case GlobalRolloutUpdated(percentage) =>
      toggle = toggle.map{ t => t.copy(rolloutPercentage = Some(percentage))}
  }

  override def receiveCommand = handleToggleCommands.orElse(handleGlobalRolloutCommands)

  def handleToggleCommands: Receive = {
    case GetToggle => sender ! toggle

    case CreateToggleCommand(name, description, tags) =>
      toggle match {
        case Some(_) => sender ! ToggleAlreadyExists(toggleId)

        case None =>
          persist(ToggleCreated(name, description, tags)) { created =>
            receiveRecover(created)
            sender ! CreateSucceeded(toggleId)
          }
      }
  }

  def handleGlobalRolloutCommands: Receive = withExistingToggle {
    t => {
      case SetGlobalRolloutCommand(p) =>
        val event = if(t.rolloutPercentage.isDefined) GlobalRolloutCreated(p) else GlobalRolloutUpdated(p)
        persist(event) { set =>
          receiveRecover(set)
          sender ! Success
        }
    }
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    sender ! PersistFailed(toggleId, cause)
  }

  def withExistingToggle(handler: Toggle => Receive): Receive = {
    case command if toggle.isDefined => handler(toggle.get)(command)
    case _                           => sender ! ToggleDoesNotExist(toggleId)
  }
}
