package toguru.toggles

import javax.inject.Inject

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import RestUtils._
import toguru.app.Config
import play.api.libs.json._
import play.api.mvc._
import ToggleActor._
import toguru.logging.EventPublishing

import scala.concurrent.{ExecutionContext, Future}

object ToggleController {

  implicit val createToggleReads = Json.reads[CreateToggleCommand]
  implicit val createToggleWrites = Json.writes[CreateToggleCommand]

  implicit val globalRolloutReads = Json.reads[CreateGlobalRolloutConditionCommand]
  implicit val globalRolloutWrites = Json.writes[CreateGlobalRolloutConditionCommand]

  implicit val updateGlobalRolloutReads = Json.reads[UpdateGlobalRolloutConditionCommand]
  implicit val updateGlobalRolloutWrites = Json.writes[UpdateGlobalRolloutConditionCommand]

  implicit val toggleWrites = Json.writes[Toggle]
  implicit val toggleReads  = Json.reads[Toggle]

  val sampleCreateToggle = CreateToggleCommand("toggle name", "toggle description", Map("team" -> "Shared Services"))
  val sampleCreateGlobalRollout = CreateGlobalRolloutConditionCommand(42)
  val sampleUpdateGlobalRollout = UpdateGlobalRolloutConditionCommand(43)

}

class ToggleController(config: Config, provider: ToggleActorProvider) extends Controller with EventPublishing {

  import ToggleController._

  implicit val timeout = Timeout(config.actorTimeout)

  type FailureHandler = PartialFunction[Throwable, Result]

  @Inject
  def this(system: ActorSystem, config: Config) = this(config, ToggleActor.provider(system))


  def get(toggleId: String) = ActionWithJson.async { request =>
    import play.api.libs.concurrent.Execution.Implicits._

    withActor(toggleId, "get-toggle") { toggleActor =>
      (toggleActor ? GetToggle).map {
        case Some(toggle: Toggle) => Ok(Json.toJson(toggle))
        case None => NotFound(errorJson(
          "Not found",
          s"A toggle with id '$toggleId' does not exist",
          "Provide an existing toggle id"))
        case r => throw new RuntimeException(s"ToggleActor replied with $r")
      }
    }
  }

  def create = ActionWithJson.async(json(sampleCreateToggle)) { request =>
    import play.api.libs.concurrent.Execution.Implicits._
    val command = request.body
    val id = ToggleActor.toId(command.name)

    withActor(id, "create-toggle") { toggleActor =>
      (toggleActor ? command).map {
        case CreateSucceeded(toggleId) =>
          publisher.event("create-toggle-success", "toggle-id" -> toggleId)
          Ok(Json.obj("status" -> "Ok", "id" -> toggleId))

        case ToggleAlreadyExists(toggleId) =>
          publisher.event("create-toggle-conflict", "toggle-id" -> toggleId)
          Conflict(errorJson(
            "Conflict",
            s"A toggle with id $toggleId already exists",
            "Choose different toggle name"))

        case PersistFailed(toggleId, cause) =>
          publisher.event("create-toggle-failure", cause, "toggle-id" -> toggleId)
          InternalServerError(errorJson("Internal Server Error", cause.getMessage))
      }
    }
  }

  def withActor(toggleId: String, actionId: String)(handler: ActorRef => Future[Result])(implicit ec: ExecutionContext): Future[Result] = {
    val toggleActor = provider.create(toggleId)
    handler(toggleActor)
      .recover(serverError(s"$actionId-failure", toggleId))
      .andThen { case _ => provider.stop(toggleActor) }
  }

  def serverError(actionId: String, name: String): FailureHandler = {
    case t: Throwable =>
      publisher.event(actionId, t, "toggleId" -> ToggleActor.toId(name))
      InternalServerError(errorJson(
        "Internal server error",
        "An internal error occurred",
        "Try again later or contact service owning team"
      ))
  }

  def createGlobalRollout(toggleId: String) = ActionWithJson.async(json(sampleCreateGlobalRollout)) { request =>
    import play.api.libs.concurrent.Execution.Implicits._
    val command = request.body

    withActor(toggleId, "create-global-rollout") { toggleActor =>
      (toggleActor ? command).map {
        case Success =>
          publisher.event("create-global-rollout-success", "toggle-id" -> toggleId)
          Ok(Json.obj("status" -> "Ok", "id" -> toggleId))

        case ToggleDoesNotExist(id) =>
          publisher.event("create-global-rollout-failure", "toggle-id" -> id)
          NotFound(errorJson(
            "Not Found",
            s"The toggle with id $id does not exist",
            "Choose an existing toggle"))

        case PersistFailed(id, cause) =>
          publisher.event("create-global-rollout-failure", cause, "toggle-id" -> id)
          InternalServerError(errorJson("Internal Server Error", cause.getMessage))
      }
    }
  }

  def updateGlobalRollout(toggleId: String) = ActionWithJson.async(json(sampleUpdateGlobalRollout))  { request =>
    import play.api.libs.concurrent.Execution.Implicits._
    val command = request.body

    withActor(toggleId, "update-global-rollout") { toggleActor =>
      (toggleActor ? command).map {
        case Success =>
          publisher.event("update-global-rollout-success", "toggle-id" -> toggleId)
          Ok(Json.obj("status" -> "Ok", "id" -> toggleId))

        case ToggleDoesNotExist(id) =>
          publisher.event("update-global-rollout-failure", "toggle-id" -> id)
          NotFound(errorJson(
            "Not Found",
            s"A toggle with id $id does not exist",
            "Choose an existing toggle"))

        case GlobalRolloutConditionDoesNotExist(id) =>
          publisher.event("update-global-rollout-failure", "toggle-id" -> id)
          NotFound(errorJson(
            "Not Found",
            s"The toggle with id $id does not have a rollout condition",
            "Please create a rollout condition"))

        case PersistFailed(id, cause) =>
          publisher.event("update-global-rollout-failure", cause, "toggle-id" -> id)
          InternalServerError(errorJson("Internal Server Error", cause.getMessage))
      }
    }
  }
}