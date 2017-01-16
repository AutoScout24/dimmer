package toguru.toggles

import java.text.SimpleDateFormat

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask
import play.api.libs.json.{Json, OWrites}
import play.api.mvc.Controller
import toguru.app.Config
import toguru.toggles.events._
import toguru.logging.EventPublishing
import toguru.toggles.AuditLogActor._

class AuditLogController@Inject()(@Named("audit-log") actor: ActorRef, config: Config) extends Controller with EventPublishing with JsonResponses with Authentication {

  val AuthenticatedWithJson = ActionWithJson andThen Authenticate(config.auth)

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

  implicit val metadataWrites = new OWrites[Metadata] {
    override def writes(o: Metadata) = Json.obj(
      "time"  -> dateFormat.format(o.time),
      "epoch" -> o.time,
      "user"  -> o.username
    )
  }
  val createdWrites = Json.writes[ToggleCreated]
  val updatedWrites = Json.writes[ToggleUpdated]
  val deletedWrites = Json.writes[ToggleDeleted]
  val rolloutCreatedWrites = Json.writes[GlobalRolloutCreated]
  val rolloutUpdatedWrites = Json.writes[GlobalRolloutUpdated]
  val rolloutDeletedWrites = Json.writes[GlobalRolloutDeleted]

  implicit val toggleEventWrites = new OWrites[AuditLog.Entry] {

    def fields(id: String, event: String) =
      Json.obj("id" -> id, "event" -> event)

    override def writes(o: AuditLog.Entry) = {
      val id = o.id
      o.event match {
        case e : ToggleCreated        => fields(id, "toggle created")  ++ createdWrites.writes(e)
        case e : ToggleUpdated        => fields(id, "toggle updated")  ++ updatedWrites.writes(e)
        case e : ToggleDeleted        => fields(id, "toggle deleted")  ++ deletedWrites.writes(e)
        case e : GlobalRolloutCreated => fields(id, "rollout created") ++ rolloutCreatedWrites.writes(e)
        case e : GlobalRolloutUpdated => fields(id, "rollout updated") ++ rolloutUpdatedWrites.writes(e)
        case e : GlobalRolloutDeleted => fields(id, "rollout deleted") ++ rolloutDeletedWrites.writes(e)
      }
    }
  }

  def get = AuthenticatedWithJson.async { request =>
    import play.api.libs.concurrent.Execution.Implicits._
    implicit val timeout = Timeout(config.actorTimeout)

    (actor ? GetLog).map {
      case l: Seq[_] =>
        val log = l.map(_.asInstanceOf[AuditLog.Entry])
        Ok(Json.toJson(log))
    }.recover(serverError("get-toggle-audit"))
  }
}
