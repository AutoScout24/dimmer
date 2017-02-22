package toguru.toggles

import akka.actor.ActorRef
import akka.pattern.ask
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.Results
import play.api.test.Helpers._
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.inject.NamedImpl
import play.mvc.Http.HeaderNames
import toguru.app.Config
import toguru.helpers.PostgresSetup
import toguru.toggles.AuditLogActor.GetLog
import toguru.toggles.ToggleStateActor.{GetState, ToggleStateInitializing}
import toguru.toggles.events.Rollout

import scala.concurrent.duration._


class ToggleIntegrationSpec extends PlaySpec
  with BeforeAndAfterAll with Results with PostgresSetup with OneServerPerSuite with FutureAwaits with DefaultAwaitTimeout with WaitFor {

  override def config = app.injector.instanceOf[Config].typesafeConfig

  override def log(message: String): Unit = info(message)

  override protected def beforeAll(): Unit = startPostgres()

  override protected def afterAll(): Unit = stopPostgres()

  def toggleAsString(name: String) =
    s"""{"name" : "$name", "description" : "toggle description", "tags" : {"team" : "Toguru team"}}"""

  "Application health check" should {
    "eventually return healthy" in {
      waitForPostgres()
      val healthURL = s"http://localhost:$port/healthcheck"
      val wsClient = app.injector.instanceOf[WSClient]

      waitFor(10.seconds, 1.seconds) {
        await(wsClient.url(healthURL).get()).status == 200
      }

      await(wsClient.url(healthURL).get()).status mustBe 200
    }
  }

  "Application ready check" should {
    "eventually return healthy" in {
      waitForPostgres()
      val healthURL = s"http://localhost:$port/readycheck"
      val wsClient = app.injector.instanceOf[WSClient]

      waitFor(10.seconds, 1.seconds) {
        await(wsClient.url(healthURL).get()).status == 200
      }

      await(wsClient.url(healthURL).get()).status mustBe 200
    }
  }

  "Toggle API" should {
    val name = "toggle 1"
    val toggleId = ToggleActor.toId(name)
    val toggleEndpointURL     = s"http://localhost:$port/toggle"
    val activationsEndpoint   = s"$toggleEndpointURL/$toggleId/activations"
    val toggleEndpoint        = s"$toggleEndpointURL/$toggleId"
    val toggleStateEndpoint   = s"http://localhost:$port/togglestate"
    val auditLogEndpoint      = s"http://localhost:$port/auditlog"
    val operationTimeout      = 30.seconds

    val wsClient = app.injector.instanceOf[WSClient]

    val validTestApiKeyHeader = HeaderNames.AUTHORIZATION -> s"${Authentication.ApiKeyPrefix} test-api-key"
    val acceptApiV2Header = HeaderNames.ACCEPT -> ToggleStateController.MimeApiV2

    def requestWithApiKeyHeader(url: String): WSRequest =
      wsClient.url(url).withHeaders(validTestApiKeyHeader)

    def fetchToggleResponse() = await(requestWithApiKeyHeader(s"$toggleEndpointURL/$toggleId").get)

    def fetchToggle(): Toggle = Json.parse(fetchToggleResponse().body).as(ToggleControllerJsonCommands.toggleFormat)

    def getActor(name: String): ActorRef = app.injector.instanceOf[ActorRef](namedKey(classOf[ActorRef], name))

    "deny access if no api key given" in {
      // prepare
      waitForPostgres()
      val body = toggleAsString(name)

      // execute
      val createResponse = await(wsClient.url(toggleEndpointURL).post(body))

      // verify
      createResponse.status mustBe UNAUTHORIZED
      val json = Json.parse(createResponse.body)
      (json \ "status").asOpt[String] mustBe Some("Unauthorized")
    }

    "create a toggle" in {
      // prepare
      waitForPostgres()
      val body = toggleAsString(name)

      // execute
      val createResponse = await(requestWithApiKeyHeader(toggleEndpointURL).post(body))
      val getResponse = await(requestWithApiKeyHeader(s"$toggleEndpointURL/$toggleId").get)

      // verify
      verifyResponseIsOk(createResponse)

      val maybeToggle = Json.parse(getResponse.body).asOpt(ToggleControllerJsonCommands.toggleFormat)
      maybeToggle mustBe Some(Toggle(toggleId, name, "toggle description", Map("team" -> "Toguru team")))
    }

    "create an activation condition" in {
      // prepare
      val body = """{ "rollout": { "percentage": 55} }"""

      // execute
      val createResponse = await(requestWithApiKeyHeader(activationsEndpoint).post(body))

      // verify
      verifyResponseIsOk(createResponse)

      fetchToggle().activations(0).rollout mustBe Some(Rollout(55))
    }

    "update an activation condition" in {
      // prepare
      val body = """{ "rollout": { "percentage": 42}, "attributes": { "my": [ "value" ] } }"""

      // execute
      val updateResponse = await(requestWithApiKeyHeader(s"$activationsEndpoint/0").put(body))

      // verify
      verifyResponseIsOk(updateResponse)

      val activation = fetchToggle().activations(0)

      activation.rollout mustBe Some(Rollout(42))
      activation.attributes mustBe Map("my" -> Seq("value"))
    }

    "reject an activation condition with rollout that is out of range" in {
      // prepare
      val body = """{ "rollout": { "percentage": 101} }"""

      // execute
      val updateResponse = await(requestWithApiKeyHeader(s"$activationsEndpoint/0").put(body))

      // verify
      updateResponse.status mustBe BAD_REQUEST
      val json = Json.parse(updateResponse.body)
      (json \ "status").asOpt[String] mustBe Some("Bad Request")

      fetchToggle().activations(0).rollout mustBe Some(Rollout(42))
    }

    "delete a global rollout condition" in {
      // execute
      val createResponse = await(requestWithApiKeyHeader(s"$activationsEndpoint/0").delete())

      // verify
      verifyResponseIsOk(createResponse)

      fetchToggle().activations mustBe empty
    }

    def waitForToggleStates(actor: ActorRef) =
      waitFor(operationTimeout, checkEvery = 1.second) {
        await(actor ? GetState) match {
          case ToggleStates(_, toggles) => toggles.size == 2
          case ToggleStateInitializing  => false
        }
      }

    "return current toggle state" in {
      // prepare
      await(requestWithApiKeyHeader(toggleEndpointURL).post(toggleAsString("toggle 2")))

      val actor = getActor("toggle-state")

      waitForToggleStates(actor)

      // execute
      val response = await(requestWithApiKeyHeader(toggleStateEndpoint).get())

      // verify
      response.status mustBe OK

      val json = Json.parse(response.body)
      json mustBe a[JsArray]
      json.asInstanceOf[JsArray].value must have size 2
    }

    "return current toggle state with APIv2" in {
      // prepare
      val actor = getActor("toggle-state")

      waitForToggleStates(actor)

      // execute
      val response = await(requestWithApiKeyHeader(toggleStateEndpoint).withHeaders(acceptApiV2Header).get())

      // verify
      response.status mustBe OK

      val json = Json.parse(response.body)
      json mustBe a[JsObject]
      json \ "toggles" match {
        case JsDefined(JsArray(seq)) => seq must have size 2
        case JsDefined(jsValue) => fail(s"'toggles' field is not an array, but $jsValue")
        case _ => fail(s"toggles field is not defined")
      }
    }

    "allow to update toggle" in {
      // execute
      val response = await(requestWithApiKeyHeader(toggleEndpoint).put(toggleAsString("toggle 3")))

      // verify
      verifyResponseIsOk(response)
    }

    "allow to delete a toggle" in {
      // execute
      val response = await(requestWithApiKeyHeader(toggleEndpoint).delete())

      // verify
      verifyResponseIsOk(response)

      fetchToggleResponse().status mustBe NOT_FOUND
    }

    "return current audit log" in {
      // prepare
      val auditLogSize = 7
      val actor = getActor("audit-log")

      waitFor(operationTimeout, checkEvery = 1.second) {
        val log = await((actor ? GetLog).mapTo[Seq[_]])
        log.size == auditLogSize
      }

      // execute
      val response = await(requestWithApiKeyHeader(auditLogEndpoint).get())

      // verify
      response.status mustBe OK

      val json = Json.parse(response.body)
      json mustBe a[JsArray]
      json.asInstanceOf[JsArray].value.size == auditLogSize
    }

    "allow to re-create a deleted toggle" in {
      // prepare
      val body = toggleAsString(name)

      // execute
      val createResponse = await(requestWithApiKeyHeader(toggleEndpointURL).post(body))

      // verify
      verifyResponseIsOk(createResponse)

      fetchToggle() mustBe Toggle(toggleId, name, "toggle description", Map("team" -> "Toguru team"))
    }
  }

  def namedKey[T](clazz: Class[T], name: String): BindingKey[T] =
    new BindingKey(clazz, Some(QualifierInstance(new NamedImpl(name))))

  def verifyResponseIsOk(response: WSResponse): Unit = {
    response.status mustBe OK
    val json = Json.parse(response.body)
    (json \ "status").asOpt[String] mustBe Some("Ok")
  }
}
