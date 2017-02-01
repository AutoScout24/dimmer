package toguru.toggles

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.codahale.metrics.Counter
import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.Json
import play.api.mvc._
import toguru.app.Config
import toguru.logging.EventPublishing
import toguru.toggles.ToggleStateActor.{GetState, ToggleStateInitializing}



object ToggleStateController {
  val MimeApiV2 = "application/vnd.toguru.v2+json"
}

class ToggleStateController(actor: ActorRef, config: Config, stateRequests: Counter, stateStaleErrors: Counter)
  extends Controller with EventPublishing with JsonResponses {

  @Inject()
  def this(@Named("toggle-state") actor: ActorRef, config: Config, metrics: Metrics) =
    this(actor, config, metrics.defaultRegistry.counter("state-requests"), metrics.defaultRegistry.counter("state-stale-errors"))

  implicit val toggleStateWriter = Json.writes[ToggleState]
  implicit val toggleStatesWriter = Json.writes[ToggleStates]
  val AcceptsToguruV2 = Accepting(ToggleStateController.MimeApiV2)

  implicit val timeout = Timeout(config.actorTimeout)

  def get(seqNo: Option[Long]) = Action.async { request =>
    import play.api.libs.concurrent.Execution.Implicits._

    stateRequests.inc()

    (actor ? GetState).map {
      case ts: ToggleStates if seqNo.exists(_ > ts.sequenceNo) =>
        stateStaleErrors.inc()
        InternalServerError(errorJson("Internal Server Error",
          "Server state is older than client state (seqNo in request is greater than server seqNo)",
          "Wait until server replays state or query another server"))
      case ts: ToggleStates =>
        Ok(jsonForRequest(request, ts))
      case ToggleStateInitializing =>
        InternalServerError(errorJson("Internal Server Error",
        "Server is currently initializing",
        "Please wait until server has completed initialization"))
    }.recover(serverError("get-toggle-state"))
  }

  def jsonForRequest(request: Request[_], toggleStates: ToggleStates) = request match {
    case Accepts.Json()    => Json.toJson(toggleStates.toggles)
    case AcceptsToguruV2() => Json.toJson(toggleStates)
  }
}
