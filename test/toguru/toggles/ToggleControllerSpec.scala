package toguru.toggles

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config => TypesafeConfig}
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import toguru.app.Config
import toguru.helpers.ControllerSpec
import toguru.toggles.ToggleActor._
import toguru.toggles.ToggleControllerJsonCommands.ActivationBody
import toguru.toggles.events.Rollout

import scala.concurrent.Future
import scala.concurrent.duration._

class ToggleControllerSpec extends ControllerSpec {

  trait ToggleControllerSetup {
    val activationBody = ActivationBody(Map("culture" -> Seq("de-DE", "de-AT")), Some(Rollout(50)))

    val activationRequest = authorizedRequest.withBody(activationBody)

    val okResponseBody = Json.obj("status" -> "Ok")
  }

  val nopActor = Props(new Actor { def receive = { case _ => () } })

  implicit val testActorSystem = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  def createController(props: Props = nopActor): ToggleController = {
    val system = ActorSystem()
    val factory = new ToggleActorProvider {
      def create(id: String): ActorRef = system.actorOf(props)

      def stop(ref: ActorRef): Unit = system.stop(ref)
    }
    val config = new Config {
      val typesafeConfig = mock[TypesafeConfig]
      val actorTimeout: FiniteDuration = 50.millis
      override def auth = authConfig
      override def auditLog = AuditLog.Config()
      override def toggleState = ToggleState.Config()
    }

    new ToggleController(config, factory)
  }

  def verifyStatus(result: Future[Result], statusCode: Int, statusString: String): JsValue =
    verifyStatus(result, statusCode, Some(statusString))

  def verifyStatus(result: Future[Result], statusCode: Int, statusString: Option[String]): JsValue = {
    val bodyJson: JsValue = contentAsJson(result)
    status(result) mustBe statusCode
    (bodyJson \ "status").asOpt[String] mustBe statusString
    bodyJson
  }

  "get method" should {
    "return toggle for an existing toggle" in {
      val props = Props(new Actor {
        def receive = { case GetToggle => sender ! Some(Toggle("toggle-id", "toggle", "desc")) }
      })

      val controller = createController(props)

      val result = controller.get("toggle-id")().apply(authorizedRequest)

      val bodyJson: JsValue = verifyStatus(result, 200, None)
      (bodyJson \ "name").asOpt[String] mustBe Some("toggle")
      (bodyJson \ "id").asOpt[String] mustBe Some("toggle-id")
    }

    "deny access when not api key given" in {
      val controller = createController()
      val request = FakeRequest()

      val result = controller.get("toggle-id")().apply(request)

      verifyStatus(result, 401, "Unauthorized")
    }

    "deny access when wrong api key given" in {
      val controller = createController()
      val request = requestWithApiKey("wrong-api-key")

      val result: Future[Result] = controller.get("toggle-id")().apply(request)

      verifyStatus(result, 401, "Unauthorized")
    }

    "return 404 for a non-existing toggle" in {
      val controller = createController(Props(new Actor {
        def receive = { case GetToggle => sender ! None }
      }))

      val result: Future[Result] = controller.get("toggle-id")().apply(authorizedRequest)

      verifyStatus(result, 404, "Not found")
    }

    "reject bad accept header" in {
      val controller = createController()
      val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> "application/bad-request-no-json-test")
      val result = controller.get("toggle-id")().apply(request)

      status(result) mustBe BAD_REQUEST
    }
  }

  "create method" should {
    "return ok when given a create command" in {
      val controller = createController(Props(new Actor {
        def receive = { case _ => sender ! CreateSucceeded("toggle-id") }
      }))
      val request = authorizedRequest.withBody(CreateToggleCommand("toggle", "description", Map.empty))

      val result = controller.create().apply(request)

      val bodyJson: JsValue = verifyStatus(result, 200, "Ok")
      (bodyJson \ "id").asOpt[String] mustBe Some("toggle-id")
    }
  }

  "update method" should {
    val updateToggle = UpdateToggleCommand(Some("toggle"), Some("description"), None)

    "return ok when given an update command" in {
      val controller = createController(Props(new Actor {
        def receive = { case _ => sender ! Success }
      }))
      val request = authorizedRequest.withBody(updateToggle)

      val result = controller.update("toggle").apply(request)

      verifyStatus(result, 200, "Ok")
    }

    "deny access when not api key given" in {
      val controller = createController()
      val request = FakeRequest().withBody(updateToggle)

      val result = controller.update("toggle").apply(request)

      verifyStatus(result, 401, "Unauthorized")
    }
  }

  "delete method" should {
    "return ok when given a success confirmation" in {
      val controller = createController(Props(new Actor {
        def receive = { case _ => sender ! Success }
      }))

      val result: Future[Result] = controller.delete("toggle").apply(authorizedRequest)

      verifyStatus(result, 200, "Ok")
    }

    "return not found when toggles does not exist" in {
      val controller = createController(Props(new Actor {
        def receive = { case _ => sender ! ToggleDoesNotExist("toggle") }
      }))

      val result = controller.delete("toggle").apply(authorizedRequest)

      verifyStatus(result, 404, "Not found")
    }

    "deny access when not api key given" in {
      val controller = createController()

      val result: Future[Result] = controller.delete("toggle").apply(FakeRequest())

      verifyStatus(result, 401, "Unauthorized")
    }
  }

  "create activation" should {
    "return ok, toggleId and actionId when called" in new ToggleControllerSetup {
      val controller = createController(Props(new Actor {
        def receive = { case _ => sender ! CreateActivationSuccess(0)  }
      }))

      val result = controller.createActivation("toggle-id")().apply(activationRequest)

      val shouldResponse =  Json.parse("""{ "status": "Ok", "index": 0 }""")

      status(result) mustBe 200
      contentAsJson(result) mustBe shouldResponse
    }

    "deny access when no api key given" in new ToggleControllerSetup {

      val controller = createController()

      val request = FakeRequest().withBody(activationBody)

      val result = controller.createActivation("toggle-id")().apply(request)

      verifyStatus(result, 401, "Unauthorized")
    }
  }

  "Update activation" should {
    "return ok, toggleId and actionId when called" in new ToggleControllerSetup {
      val controller = createController(Props(new Actor {
        def receive = { case _ => sender ! Success }
      }))

      val result = controller.updateActivation("toggle-id", 0)().apply(activationRequest)

      val shouldResponse =  Json.parse("""{ "status": "Ok"}""")

      status(result) mustBe 200
      contentAsJson(result) mustBe shouldResponse
    }

    "accept activation without attributes" in new ToggleControllerSetup {
      var command: Option[UpdateActivationCommand] = None
      val controller = createController(Props(new Actor {
        def receive = { case AuthenticatedCommand(c : UpdateActivationCommand, _) =>
          command = Some(c)
          sender ! Success
        }
      }))

      val result = controller.updateActivation("toggle-id", 0)().apply(activationRequest)

      status(result) mustBe 200
      contentAsJson(result) mustBe okResponseBody
      command.value.percentage mustBe Some(Rollout(50))
    }

    "deny access when no api key given" in new ToggleControllerSetup {

      val controller = createController()

      val request = FakeRequest().withBody(activationBody)

      val result = controller.updateActivation("toggle-id", 0).apply(request)

      verifyStatus(result, 401, "Unauthorized")
    }

    "return not found when toggle does not exist" in new ToggleControllerSetup {
      val controller = createController(Props(new Actor {
        def receive = { case _ => sender ! ToggleDoesNotExist("toggle") }
      }))

      val result = controller.updateActivation("toggle-id", 0).apply(activationRequest)

      verifyStatus(result, 404, "Not found")
    }
  }

  "delete activation" should {
    "return status ok when toggle is deleted" in new ToggleControllerSetup {
      var command: Option[DeleteActivationCommand] = None
      val controller = createController(Props(new Actor {
        def receive = { case AuthenticatedCommand(c : DeleteActivationCommand, _) =>
          command = Some(c)
          sender ! Success
        }
      }))

      val request = authorizedRequest

      val result = controller.deleteActivation("toggle-id", 0)().apply(request)

      status(result) mustBe 200
      contentAsJson(result) mustBe okResponseBody
      command.value.index mustBe 0
    }

    "should deny access when no api key given" in {

      val controller = createController()

      val request = FakeRequest()

      val result = controller.deleteActivation("toggle-id", 0)().apply(request)

      verifyStatus(result, 401, "Unauthorized")
    }
  }

  "withActor" should {
    "return 500 when actor request times out" in {
      val controller = createController(Props(new Actor {
        override def receive = { case _ => () }
      }))
      val timeout = Timeout(50.millis)
      implicit val actionId = "action-id"
      import play.api.libs.concurrent.Execution.Implicits._

      val result = controller.withActor("toggle-id") { actor =>
        (actor ? GetToggle)(timeout).map(_ => Ok("Ok"))
      }

      val bodyJson: JsValue = verifyStatus(result, 500, "Internal server error")
      (bodyJson \ "reason").asOpt[String] mustBe Some("An internal error occurred")
    }
  }
}
