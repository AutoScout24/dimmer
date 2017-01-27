package toguru.toggles

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.codahale.metrics.Counter
import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._
import toguru.app.Config
import toguru.logging.EventPublishing
import toguru.toggles.ToggleStateActor.GetState



object ToggleStateController {
  val MimeApiV2 = "application/vnd.toguru.v2+json"
}

class ToggleStateController(actor: ActorRef, config: Config, stateRequests: Counter)
  extends Controller with EventPublishing with JsonResponses {

  @Inject()
  def this(@Named("toggle-state") actor: ActorRef, config: Config, metrics: Metrics) =
    this(actor, config, metrics.defaultRegistry.counter("state-requests"))

  implicit val toggleStateWriter = Json.writes[ToggleState]
  implicit val toggleStatesWriter = Json.writes[ToggleStates]
  val AcceptsToguruV2 = Accepting(ToggleStateController.MimeApiV2)


  def get = Action.async { request =>
    Logger.info(s"REQUEST HEADERS: ${request.headers.toSimpleMap}")
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val timeout = Timeout(config.actorTimeout)

    stateRequests.inc()

    (actor ? GetState).map {
      case m: ToggleStates =>
        Ok(jsonForRequest(request, m))
    }.recover(serverError("get-toggle-state"))
  }

  def jsonForRequest(request: Request[_], toggleStates: ToggleStates) = request match {
    case Accepts.Json()    => Json.toJson(toggleStates.toggles)
    case AcceptsToguruV2() => Json.toJson(toggleStates)
  }
}
