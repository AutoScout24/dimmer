package toguru.toggles

import akka.persistence._
import toguru.logging.EventPublishing
import akka.actor.{ActorRef, ActorSystem, Props}
import ToggleActor._
import toguru.toggles.events._
import toguru.toggles.snapshots._
import toguru.toggles.Authentication.Principal

trait ToggleActorProvider {
  def create(id: String): ActorRef
  def stop(ref: ActorRef)
}

object ToggleActor {

  case class CreateToggleCommand(name: String, description: String, tags: Map[String, String])

  case class CreateSucceeded(id: String)

  case class ToggleAlreadyExists(id: String)

  case class UpdateToggleCommand(name: Option[String], description: Option[String], tags: Option[Map[String, String]])

  case object DeleteToggleCommand

  case object GetToggle

  case class AuthenticatedCommand[T](command: T, user: Principal)

  case object AuthenticationMissing

  case class SetGlobalRolloutCommand(percentage: Int)

  case object DeleteGlobalRolloutCommand

  case object Success

  case class ToggleDoesNotExist(id: String)

  case class PersistFailed(id: String, cause: Throwable)

  case object Shutdown

  def toId(name: String): String = name.trim.toLowerCase.replaceAll("\\s+", "-")

  def apply(id: String): Props = Props(new ToggleActor(id))

  def provider(system: ActorSystem) = new ToggleActorProvider {

    def create(id: String): ActorRef = system.actorOf(ToggleActor(id))

    def stop(ref: ActorRef): Unit = ref ! Shutdown
  }
}

class ToggleActor(toggleId: String, var maybeToggle: Option[Toggle] = None) extends PersistentActor with EventPublishing {

  val persistenceId = toggleId

  var snapshotAge = 0

   override def receiveCommand = maybeToggle.fold(initial)(existing)

  def existing(t: Toggle): Receive = globalCommands.orElse(snapshotCommands).orElse(withMetadata(m => existingToggle(t, m)))

  def initial: Receive = globalCommands.orElse(snapshotCommands).orElse(withMetadata(m => nonExistingToggle(m)))

  def globalCommands: Receive = {
    case Shutdown => context.stop(self)

    case GetToggle => sender ! maybeToggle
  }

  def nonExistingToggle(meta: Option[Metadata]): Receive = {
    case CreateToggleCommand(name, description, tags) =>
      persist(ToggleCreated(name, description, tags, meta)) { created =>
        receiveRecover(created)
        sender ! CreateSucceeded(toggleId)
      }

    case _ => sender ! ToggleDoesNotExist(toggleId)
  }

  def existingToggle(t: Toggle, meta: Option[Metadata]): Receive = {
    case CreateToggleCommand(name, description, tags) =>
      sender ! ToggleAlreadyExists(t.id)

    case UpdateToggleCommand(name, description, tags) =>
      val updated = ToggleUpdated(
        name.getOrElse(t.name), description.getOrElse(t.description), tags.getOrElse(t.tags), meta)
      persist(updated) { updated =>
        receiveRecover(updated)
        sender ! Success
      }

    case DeleteToggleCommand =>
      persist(ToggleDeleted(meta)) { deleted =>
        receiveRecover(deleted)
        sender ! Success
      }

    case SetGlobalRolloutCommand(p) =>
      val event = if(t.rolloutPercentage.isDefined) GlobalRolloutUpdated(p, meta) else GlobalRolloutCreated(p, meta)
      persist(event) { event =>
        receiveRecover(event)
        sender ! Success
      }

    case DeleteGlobalRolloutCommand =>
      t.rolloutPercentage match {
        case Some(_) =>
          persist(GlobalRolloutDeleted(meta)) { deleted =>
            receiveRecover(deleted)
            sender ! Success
          }
        case None => sender ! Success
      }
  }

  def snapshotCommands: Receive = {
    case SaveSnapshotSuccess(metadata) =>
      snapshotAge = 0
      deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = metadata.sequenceNr - 1))

    case DeleteSnapshotsSuccess(_) => ()

    case DeleteSnapshotFailure(meta, cause) =>
      publisher.event("toggle-delete-snapshot-failed", cause, "toggleId" -> persistenceId)

    case SaveSnapshotFailure(_, cause) =>
      publisher.event("toggle-create-snapshot-failed", cause, "toggleId" -> persistenceId)
  }

  override def receiveRecover = {
    case SnapshotOffer(metadata, s: ToggleSnapshot) =>
      if(s.exists) {
        maybeToggle = Some(Toggle(
          id = toggleId,
          name = s.name,
          description = s.description,
          tags = s.tags,
          rolloutPercentage = if(s.percentage == -1) None else Some(s.percentage)))
      } else {
        maybeToggle = None
      }
      snapshotAge = 0

    case ToggleCreated(name, description, tags, _) =>
      val toggle = Toggle(toggleId, name, description, tags)
      maybeToggle = Some(toggle)
      context.become(existing(toggle))
      maybeSnapshot()

    case ToggleDeleted(_) =>
      maybeToggle = None
      context.become(initial)
      maybeSnapshot()

    case ToggleUpdated(name, description, tags, _) =>
      updateToggle(_.copy(name = name, description = description, tags = tags))

    case GlobalRolloutCreated(p, _) =>
      updateToggle(_.copy(rolloutPercentage = Some(p)))

    case GlobalRolloutUpdated(p, _) =>
      updateToggle(_.copy(rolloutPercentage = Some(p)))

    case GlobalRolloutDeleted(_) =>
      updateToggle(_.copy(rolloutPercentage = None))
  }

  def updateToggle(update: Toggle => Toggle) = {
    maybeToggle = maybeToggle.map(update)
    context.become(receiveCommand)
    maybeSnapshot()
  }

  def maybeSnapshot() = {
    snapshotAge += 1
    if(snapshotAge >= 10) {
      val snapshot = maybeToggle.fold(ToggleSnapshot().withExists(false)) { t =>
        ToggleSnapshot(
          exists = true,
          name = t.name,
          description = t.description,
          tags = t.tags,
          percentage = t.rolloutPercentage.getOrElse(-1))
      }
      saveSnapshot(snapshot)
    }
  }

  override protected def onPersistFailure(cause: Throwable, event: Any, seqNr: Long) = {
    publisher.event("toggle-persist-failed", cause, "toggleId" -> persistenceId, "eventType" -> event.getClass.getSimpleName)
    sender ! PersistFailed(toggleId, cause)
  }

  override protected def onPersistRejected(cause: Throwable, event: Any, seqNr: Long) = {
    publisher.event("toggle-persist-rejected", cause, "toggleId" -> persistenceId, "eventType" -> event.getClass.getSimpleName)
    sender ! PersistFailed(toggleId, cause)
  }

  def time() = System.currentTimeMillis

  def metadata(user: Principal) = Some(Metadata(time(), user.name))

  def withMetadata(handler: Option[Metadata] => Receive): Receive = {
    case AuthenticatedCommand(command, principal) => handler(metadata(principal))(command)
    case _                                        => sender ! AuthenticationMissing
  }
}
