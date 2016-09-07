package toguru.toggles

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json.Json
import play.api.mvc.Controller
import toguru.app.Config
import toguru.logging.EventPublishing
import toguru.toggles.ToggleStateActor.GetState

class ToggleStateController @Inject()(@Named("toggle-state") actor: ActorRef, config: Config) extends Controller with EventPublishing with JsonResponses {

  implicit val toggleStateWriter = Json.writes[ToggleState]

  def get = ActionWithJson.async { request =>
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val timeout = Timeout(config.actorTimeout)

    (actor ? GetState).map {
      case m: Map[_, _] =>
        val toggles = m.values.map(_.asInstanceOf[ToggleState]).to[Vector].sortBy(_.id)
        Ok(Json.toJson(toggles))
    }.recover(serverError("get-toggle-state"))
  }
}
