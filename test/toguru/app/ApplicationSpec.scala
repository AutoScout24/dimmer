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
    val bodyText = contentAsString(result)
    bodyText mustBe "Ok"
  }

  "ready check should return ok" in {

    val controller = createAppController()
    val result = controller.readyCheck().apply(FakeRequest(GET, "/readycheck"))
    val bodyText = contentAsString(result)
    bodyText mustBe "Ok"
  }

  "ready check should return database not available" in {
    val controller = createAppController(
      Props(new Actor { def receive = { case _ => sender ! HealthStatus(false) } })
    )
    val result = controller.readyCheck().apply(FakeRequest(GET, "/readycheck"))
    val bodyText = contentAsString(result)
    bodyText mustBe "Database not available"
  }

  val healthyProps = Props(new Actor { def receive = { case _ => sender ! HealthStatus(true) } })

  def createAppController(props: Props =  healthyProps): Application =
    new Application(ActorSystem().actorOf(props))
}
