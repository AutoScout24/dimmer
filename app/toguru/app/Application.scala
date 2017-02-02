package toguru.app

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json.Json
import toguru.app.HealthActor.{GetHealth, HealthStatus}
import play.api.mvc._
import toguru.app.Application.{Check, Health, Readiness}

import scala.concurrent.Future
import scala.concurrent.duration._

object Application {
  sealed trait Check
  case object Health extends Check
  case object Readiness extends Check
}

class Application @Inject() (@Named("health") healthActor: ActorRef) extends Controller {

  def healthCheck = Action.async { handleHealthRequest(Health) }

  def readyCheck  = Action.async {  handleHealthRequest(Readiness) }

  private def handleHealthRequest(check: Check): Future[Result] = {
    import play.api.libs.concurrent.Execution.Implicits._

    implicit val timeout = Timeout(500.milliseconds)
    (healthActor ? GetHealth())
      .mapTo[HealthStatus]
      .map(toResponse(check))
  }

  implicit val healthWrites = Json.writes[HealthStatus]

  private def toResponse(check: Check)(health: HealthStatus): Result = {
    val content = Json.toJson(health)
    val ok = check match {
      case Health    => health.healthy
      case Readiness => health.ready
    }

    if (ok) Ok(content) else InternalServerError(content)
  }
}
