package toguru.app

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import toguru.app.HealthActor.{GetHealth, HealthStatus}
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration._

class Application @Inject() (@Named("health") healthActor: ActorRef) extends Controller {

  def healthCheck = Action.async { handleHealthRequest(Ok) }

  def readyCheck  = Action.async {  handleHealthRequest() }

  private def handleHealthRequest(databaseUnavailableStatus: Status = InternalServerError): Future[Result] = {
    import play.api.libs.concurrent.Execution.Implicits._

    implicit val timeout = Timeout(500.milliseconds)
    (healthActor ? GetHealth())
      .mapTo[HealthStatus]
      .map(toResponse(databaseUnavailableStatus))
  }

  private def toResponse(databaseUnavailableStatus: Status)(health: HealthStatus): Result =
    if (health.isDatabaseHealthy) Ok("Ok") else databaseUnavailableStatus("Database not available")
}
