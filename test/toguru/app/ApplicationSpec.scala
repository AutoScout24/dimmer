package toguru.app

import akka.actor.{Props, ActorSystem, Actor}
import toguru.app.HealthActor.HealthStatus
import org.scalatestplus.play._

import play.api.mvc._
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.Future

class ApplicationSpec extends PlaySpec with Results {

  "health check" should {
    "return ok if DB and toggle state healthy" in {
      val controller = createAppController()

      val result = controller.healthCheck().apply(FakeRequest())

      val bodyJson = contentAsJson(result)
      status(result) mustBe 200
      (bodyJson \ "databaseHealthy").as[Boolean] mustBe true
      (bodyJson \ "toggleStateHealthy").as[Boolean] mustBe true
    }

    "return server eror when toggle state unhealthy" in {
      val controller = createAppController(
        Props(new Actor { def receive = { case _ => sender ! HealthStatus(true, false) } })
      )

      val result = controller.healthCheck().apply(FakeRequest())

      val bodyJson = contentAsJson(result)
      status(result) mustBe 500
      (bodyJson \ "toggleStateHealthy").as[Boolean] mustBe false
    }
  }

  "ready check" should {
    "return ok if DB and toggle state healthy" in {
      val controller = createAppController()

      val result = controller.readyCheck().apply(FakeRequest())

      val bodyJson = contentAsJson(result)
      status(result) mustBe 200
      (bodyJson \ "databaseHealthy").as[Boolean] mustBe true
      (bodyJson \ "toggleStateHealthy").as[Boolean] mustBe true
    }

    "return server error when database unhealthy" in {
      val controller = createAppController(
        Props(new Actor { def receive = { case _ => sender ! HealthStatus(false, true) } })
      )

      val result = controller.readyCheck().apply(FakeRequest())

      val bodyJson = contentAsJson(result)
      status(result) mustBe 500
      (bodyJson \ "databaseHealthy").as[Boolean] mustBe false
    }
  }

  val healthyProps = Props(new Actor { def receive = { case _ => sender ! HealthStatus(true, true) } })

  def createAppController(props: Props =  healthyProps): Application =
    new Application(ActorSystem().actorOf(props))
}
