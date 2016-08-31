package toguru.toggles

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config => TypesafeConfig}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.JsValue
import play.api.mvc.{Result, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import toguru.app.Config
import toguru.toggles.ToggleActor._

import scala.concurrent.Future
import scala.concurrent.duration._

class ToggleControllerSpec extends PlaySpec with Results with MockitoSugar {

  def createController(props: Props = Props(new MockToggleActor())): ToggleController = {
    val system = ActorSystem()
    val factory = new ToggleActorProvider {
      def create(id: String): ActorRef = system.actorOf(props)

      def stop(ref: ActorRef): Unit = system.stop(ref)
    }
    val config = new Config {
      val typesafeConfig = mock[TypesafeConfig]
      val actorTimeout: FiniteDuration = 50.millis
    }

    new ToggleController(config, factory)
  }

  "get method" should {
    "return toggle for an existing toggle" in {
      val controller = createController()
      val request = FakeRequest()

      val result: Future[Result] = controller.get("toggle-id")().apply(request)

      val bodyJson: JsValue = contentAsJson(result)
      status(result) mustBe 200
      (bodyJson \ "name").asOpt[String] mustBe Some("toggle")
      (bodyJson \ "id").asOpt[String] mustBe Some("toggle-id")
    }

    "return 404 for a non-existing toggle" in {

      val props = Props(new Actor {
        def receive: Receive = {
          case GetToggle => sender ! None
        }
      })
      val controller = createController(props)
      val request = FakeRequest()

      val result: Future[Result] = controller.get("toggle-id")().apply(request)

      val bodyJson: JsValue = contentAsJson(result)
      status(result) mustBe 404
      (bodyJson \ "status").asOpt[String] mustBe Some("Not found")
    }
  }

  "create method" should {
    "return ok when given a create command" in {
      val controller = createController()
      val request = FakeRequest().withBody(CreateToggleCommand("toggle", "description", Map.empty))

      val result: Future[Result] = controller.create().apply(request)

      val bodyJson: JsValue = contentAsJson(result)
      status(result) mustBe 200
      (bodyJson \ "status").asOpt[String] mustBe Some("Ok")
      (bodyJson \ "id").asOpt[String] mustBe Some("toggle-id")
    }
  }

  "set rollout condition" should {
    "return ok when given a create command" in {
      val controller = createController()
      val request = FakeRequest().withBody(CreateGlobalRolloutConditionCommand(42))

      val result: Future[Result] = controller.createGlobalRollout("toggle-id").apply(request)

      val bodyJson: JsValue = contentAsJson(result)
      status(result) mustBe 200
      (bodyJson \ "status").asOpt[String] mustBe Some("Ok")
      (bodyJson \ "id").asOpt[String] mustBe Some("toggle-id")
    }

    "returns not found when toggle does not exist" in {
      val controller = createController(Props(new Actor{
        override def receive = { case _ => sender ! ToggleDoesNotExist("toggle-id") }
      }))
      val request = FakeRequest().withBody(CreateGlobalRolloutConditionCommand(42))

      val result: Future[Result] = controller.createGlobalRollout("toggle-id").apply(request)

      val bodyJson: JsValue = contentAsJson(result)
      status(result) mustBe 404
    }
  }

  "withActor" should {
    "return 500 when actor request times out" in {
      val controller = createController(Props(new MockLazyActor()))
      val timeout = Timeout(50.millis)
      import play.api.libs.concurrent.Execution.Implicits._

      val result = controller.withActor("toggle-id", "action-id") { actor =>
        (actor ? GetToggle)(timeout).map(_ => Ok("Ok"))
      }

      val bodyJson: JsValue = contentAsJson(result)
      status(result) mustBe 500
      (bodyJson \ "status").asOpt[String] mustBe Some("Internal server error")
      (bodyJson \ "reason").asOpt[String] mustBe Some("An internal error occurred")
    }
  }

  class MockToggleActor extends Actor {
    def receive: Receive = {
      case GetToggle               => sender ! Some(Toggle("toggle-id", "toggle", "desc", Map("team" -> "SharedServices"), None))
      case _ : CreateGlobalRolloutConditionCommand => sender ! Success
      case _ : CreateToggleCommand => sender ! CreateSucceeded("toggle-id")
    }
  }

  class MockLazyActor extends Actor {
    def receive: Receive = {
      case _ => ()
    }
  }
}
