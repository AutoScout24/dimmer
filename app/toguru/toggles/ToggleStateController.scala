package toguru.toggles

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.codahale.metrics.Counter
import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.Json
import play.api.mvc.Controller
import toguru.app.Config
import toguru.logging.EventPublishing
import toguru.toggles.ToggleStateActor.GetState

class ToggleStateController(actor: ActorRef, config: Config, stateRequests: Counter)
  extends Controller with EventPublishing with JsonResponses {

  @Inject()
  def this(@Named("toggle-state") actor: ActorRef, config: Config, metrics: Metrics) =
    this(actor, config, metrics.defaultRegistry.counter("state-requests"))

  implicit val toggleStateWriter = Json.writes[ToggleState]

  def get = ActionWithJson.async { request =>
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val timeout = Timeout(config.actorTimeout)

    stateRequests.inc()

    (actor ? GetState).map {
      case m: Map[_, _] =>
        val toggles = m.values.map(_.asInstanceOf[ToggleState]).to[Vector].sortBy(_.id)
        Ok(Json.toJson(toggles))
    }.recover(serverError("get-toggle-state"))
  }
}
