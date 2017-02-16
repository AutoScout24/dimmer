package toguru.toggles

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.codahale.metrics.Counter
import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.libs.functional.syntax._
import play.api.mvc._
import toguru.app.Config
import toguru.logging.EventPublishing
import toguru.toggles.ToggleStateActor.{GetState, ToggleStateInitializing}
import toguru.toggles.events.Rollout



object ToggleStateController {
  val MimeApiV2 = "application/vnd.toguru.v2+json"
  val MimeApiV3 = "application/vnd.toguru.v3+json"

  val toggleStateWriterUntilV2: Writes[ToggleState] = (
      (JsPath \ "id").write[String] and
      (JsPath \ "tags").write[Map[String, String]] and
      (JsPath \ "rolloutPercentage").writeNullable[Int]
    )(ts => (ts.id, ts.tags, ts.activations.headOption.flatMap(_.rollout.map(_.percentage)) ))

  val toggleStateSeqWriterUntilV2 = Writes.seq(toggleStateWriterUntilV2)

  val toggleStatesWriterUntilV2: Writes[ToggleStates] = (
      (JsPath \ "sequenceNo").write[Long] and
      (JsPath \ "toggles").write(Writes.seq(toggleStateWriterUntilV2))
    )(unlift(ToggleStates.unapply))

  implicit val rolloutWriter = Json.writes[Rollout]
  implicit val toggleActivationWriter = Json.writes[ToggleActivation]
  implicit val toggleStateWriter = Json.writes[ToggleState]
  implicit val toggleStatesWriter = Json.writes[ToggleStates]

  val AcceptsToguruV2 = Accepting(MimeApiV2)
  val AcceptsToguruV3 = Accepting(MimeApiV3)
}

class ToggleStateController(actor: ActorRef, config: Config, stateRequests: Counter, stateStaleErrors: Counter)
  extends Controller with EventPublishing with JsonResponses {

  import ToggleStateController._

  @Inject()
  def this(@Named("toggle-state") actor: ActorRef, config: Config, metrics: Metrics) =
    this(actor, config, metrics.defaultRegistry.counter("state-requests"), metrics.defaultRegistry.counter("state-stale-errors"))

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
        responseFor(request, ts)
      case ToggleStateInitializing =>
        InternalServerError(errorJson("Internal Server Error",
        "Server is currently initializing",
        "Please wait until server has completed initialization"))
    }.recover(serverError("get-toggle-state"))
  }

  def responseFor(request: Request[_], toggleStates: ToggleStates) = request match {
    case Accepts.Json()    => Ok(Json.toJson(toggleStates.toggles)(toggleStateSeqWriterUntilV2))
    case AcceptsToguruV2() => Ok(Json.toJson(toggleStates)(toggleStatesWriterUntilV2)).as(MimeApiV2)
    case AcceptsToguruV3() => Ok(Json.toJson(toggleStates)).as(MimeApiV3)
  }
}
