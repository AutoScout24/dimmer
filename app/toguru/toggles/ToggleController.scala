package toguru.toggles

import javax.inject.Inject

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.mvc._
import toguru.app.Config
import toguru.logging.EventPublishing
import toguru.toggles.Authentication.AuthenticatedRequest
import toguru.toggles.ToggleActor._

import scala.concurrent.{ExecutionContext, Future}

object ToggleController {

  implicit val toggleFormat = Json.format[Toggle]
  implicit val createToggleFormat = Json.format[CreateToggleCommand]
  implicit val updateToggleFormat = Json.format[UpdateToggleCommand]
  implicit val globalRolloutFormat: Format[SetGlobalRolloutCommand] =
    (JsPath \ "percentage").format[Int](min(1) keepAnd max(100)).inmap(
      SetGlobalRolloutCommand.apply, unlift(SetGlobalRolloutCommand.unapply))

  val sampleCreateToggle = CreateToggleCommand("toggle name", "toggle description", Map("team" -> "Toguru team"))
  val sampleUpdateToggle = UpdateToggleCommand(None, Some("new toggle description"), Some(Map("team" -> "Toguru team")))
  val sampleSetGlobalRollout = SetGlobalRolloutCommand(42)
}

class ToggleController(config: Config, provider: ToggleActorProvider) extends Controller with EventPublishing with Authentication with Results with JsonResponses with ToggleActorResponses {
  import ToggleController._

  val AuthenticatedWithJson: ActionBuilder[AuthenticatedRequest] = ActionWithJson andThen Authenticate(config.auth)

  def authenticated[T](command: T)(implicit request: AuthenticatedRequest[_]) =
    AuthenticatedCommand(command, request.principal)

  implicit val timeout = Timeout(config.actorTimeout)

  @Inject
  def this(system: ActorSystem, config: Config) = this(config, ToggleActor.provider(system))

  def withActor(toggleId: String)(handler: ActorRef => Future[Result])(implicit actionId: String, ec: ExecutionContext): Future[Result] = {
    val toggleActor = provider.create(toggleId)
    handler(toggleActor)
      .recover(serverError(actionId, "toggleId" -> toggleId))
      .andThen { case _ => provider.stop(toggleActor) }
  }

  def get(toggleId: String) = AuthenticatedWithJson.async { request =>
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val actionId = "get-toggle"

    withActor(toggleId) { toggleActor =>
      (toggleActor ? GetToggle).map {
        case Some(toggle: Toggle) =>
          Ok(Json.toJson(toggle))
        case None =>
          NotFound(errorJson("Not found", s"A toggle with id '$toggleId' does not exist", "Provide an existing toggle id"))
        case r =>
          throw new RuntimeException(s"ToggleActor replied with $r")
      }
    }
  }

  def create = AuthenticatedWithJson.async(json(sampleCreateToggle)) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._
    val command  = authenticated(request.body)
    val toggleId = ToggleActor.toId(request.body.name)
    implicit val actionId = "create-toggle"

    withActor(toggleId) { toggleActor =>
      (toggleActor ? command).map(whenPersisted {
        case CreateSucceeded(id) =>
          publishSuccess(actionId, id)
          Ok(Json.obj("status" -> "Ok", "id" -> id))

        case ToggleAlreadyExists(id) =>
          publisher.event(s"$actionId-conflict", "toggleId" -> id)
          Conflict(errorJson("Conflict", s"A toggle with id $id already exists", "Choose different toggle name"))
      })
    }
  }

  def update(toggleId: String) = AuthenticatedWithJson.async(json(sampleUpdateToggle)) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._
    val command  = authenticated(request.body)
    implicit val actionId = "update-toggle"

    withActor(toggleId) { toggleActor =>
      (toggleActor ? command).map(
        both(whenToggleExists, whenPersisted) {
          case Success =>
            publishSuccess(actionId, toggleId)
            Ok(Json.obj("status" -> "Ok"))
      })
    }
  }

  def delete(toggleId: String) = AuthenticatedWithJson.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val actionId = "delete-toggle"

    withActor(toggleId) { toggleActor =>
      (toggleActor ? authenticated(DeleteToggleCommand)).map(
        both(whenToggleExists, whenPersisted) {
          case Success =>
            publishSuccess(actionId, toggleId)
            Ok(Json.obj("status" -> "Ok"))
        })
    }
  }

  def setGlobalRollout(toggleId: String) = AuthenticatedWithJson.async(json(sampleSetGlobalRollout)) { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._
    val command = authenticated(request.body)
    implicit val actionId = "set-global-rollout"

    withActor(toggleId) { toggleActor =>
      (toggleActor ? command).map(
        both(whenToggleExists, whenPersisted) {
          case Success =>
            publishSuccess(actionId, toggleId)
            Ok(Json.obj("status" -> "Ok"))
        })
    }
  }

  def deleteGlobalRollout(toggleId: String) = AuthenticatedWithJson.async { implicit request =>
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val actionId = "delete-global-rollout"

    withActor(toggleId) { toggleActor =>
      (toggleActor ? authenticated(DeleteGlobalRolloutCommand)).map(
        both(whenToggleExists, whenPersisted) {
          case Success =>
            publishSuccess(actionId, toggleId)
            Ok(Json.obj("status" -> "Ok"))
        })
    }
  }
}
