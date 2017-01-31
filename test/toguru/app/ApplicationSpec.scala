package toguru.app

import akka.actor.{Props, ActorSystem, Actor}
import toguru.app.HealthActor.HealthStatus
import org.scalatestplus.play._

import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.Future

class ApplicationSpec extends PlaySpec with Results {

  "health check should return ok" in {

    val controller = createAppController()
    val result = controller.healthCheck().apply(FakeRequest(GET, "/healthcheck"))
    val bodyJson = contentAsJson(result)
    (bodyJson \ "databaseHealthy").as[Boolean] mustBe true
    (bodyJson \ "toggleStateHealthy").as[Boolean] mustBe true
  }

  "ready check should return ok" in {

    val controller = createAppController()
    val result = controller.readyCheck().apply(FakeRequest(GET, "/readycheck"))
    val bodyJson = contentAsJson(result)
    (bodyJson \ "databaseHealthy").as[Boolean] mustBe true
    (bodyJson \ "toggleStateHealthy").as[Boolean] mustBe true
  }

  "ready check should return database not available" in {
    val controller = createAppController(
      Props(new Actor { def receive = { case _ => sender ! HealthStatus(false, true) } })
    )
    val result = controller.readyCheck().apply(FakeRequest(GET, "/readycheck"))
    val bodyJson = contentAsJson(result)
    (bodyJson \ "databaseHealthy").as[Boolean] mustBe false
  }

  val healthyProps = Props(new Actor { def receive = { case _ => sender ! HealthStatus(true, true) } })

  def createAppController(props: Props =  healthyProps): Application =
    new Application(ActorSystem().actorOf(props))
}
