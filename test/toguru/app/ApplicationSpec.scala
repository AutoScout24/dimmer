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

    val controller: Application = createAppController
    val result: Future[Result] = controller.healthCheck().apply(FakeRequest(GET, "/healthcheck"))
    val bodyText: String = contentAsString(result)
    bodyText mustBe "Ok"
  }

  def createAppController: Application =
    new Application(ActorSystem().actorOf(Props(new Actor {
      def receive = { case _ => sender ! HealthStatus(true) }
    })))
}
