package toguru.toggles

import java.text.SimpleDateFormat

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Controller
import toguru.app.Config
import toguru.events.toggles._
import toguru.logging.EventPublishing
import toguru.toggles.AuditLogActor.GetLog

class AuditLogController@Inject()(@Named("audit-log") actor: ActorRef, config: Config) extends Controller with EventPublishing with JsonResponses {

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

  implicit val metadataWrites = new OWrites[Metadata] {
    override def writes(o: Metadata) = Json.obj(
      "time"  -> dateFormat.format(o.time),
      "epoch" -> o.time,
      "user"  -> o.username
    )
  }
  val createdWrites = Json.writes[ToggleCreated]
  val rolloutCreatedWrites = Json.writes[GlobalRolloutCreated]
  val rolloutUpdatedWrites = Json.writes[GlobalRolloutUpdated]
  val rolloutDeletedWrites = Json.writes[GlobalRolloutDeleted]

  implicit val toggleEventWrites = new OWrites[(String, ToggleEvent)] {

    def fields(id: String, event: String) =
      Json.obj("id" -> id, "event" -> event)

    override def writes(o: (String, ToggleEvent)) =
      o._2 match {
        case e : ToggleCreated        => fields(o._1, "toggle created")  ++ createdWrites.writes(e)
        case e : GlobalRolloutCreated => fields(o._1, "rollout created") ++ rolloutCreatedWrites.writes(e)
        case e : GlobalRolloutUpdated => fields(o._1, "rollout updated") ++ rolloutUpdatedWrites.writes(e)
        case e : GlobalRolloutDeleted => fields(o._1, "rollout deleted") ++ rolloutDeletedWrites.writes(e)
      }
  }

  def get = ActionWithJson.async { request =>
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val timeout = Timeout(config.actorTimeout)

    (actor ? GetLog).map {
      case l: Seq[_] =>
        val log = l.map(_.asInstanceOf[(String,ToggleEvent)])
        Ok(Json.toJson(log))
    }.recover(serverError("get-toggle-audit"))
  }
}
