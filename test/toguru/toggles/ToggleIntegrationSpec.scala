package toguru.toggles

import akka.actor.ActorRef
import akka.pattern.ask
import toguru.PostgresSetup
import toguru.app.Config
import play.api.test.Helpers._
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.inject.{BindingKey, QualifierInstance}
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.Results
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import play.inject.NamedImpl
import toguru.toggles.AuditLogActor.GetLog
import toguru.toggles.ToggleStateActor.GetState

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration


class ToggleIntegrationSpec extends PlaySpec
  with BeforeAndAfterAll with Results with PostgresSetup with OneServerPerSuite with FutureAwaits with DefaultAwaitTimeout {

  override def config = app.injector.instanceOf[Config].typesafeConfig

  override def log(message: String): Unit = info(message)

  override protected def beforeAll(): Unit = startPostgres()

  override protected def afterAll(): Unit = stopPostgres()

  def toggleAsString(name: String) =
    s"""{"name" : "$name", "description" : "toggle description", "tags" : {"team" : "Toguru team"}}"""

  "Toggle API" should {
    val name = "toggle 1"
    val toggleId = ToggleActor.toId(name)
    val toggleEndpointURL     = s"http://localhost:$port/toggle"
    val globalRolloutEndpoint = s"$toggleEndpointURL/$toggleId/globalrollout"
    val toggleUpdateEndpoint  = s"$toggleEndpointURL/$toggleId"
    val toggleStateEndpoint   = s"http://localhost:$port/togglestate"
    val auditLogEndpoint      = s"http://localhost:$port/auditlog"

    val wsClient = app.injector.instanceOf[WSClient]

    def fetchToggle(): Toggle = {
      val getResponse = await(wsClient.url(s"$toggleEndpointURL/$toggleId").get)
      Json.parse(getResponse.body).as(ToggleController.toggleFormat)
    }

    "create a toggle" in {
      // prepare
      waitForPostgres()

      val body = toggleAsString(name)
      // execute
      val createResponse = await(wsClient.url(toggleEndpointURL).post(body))
      val getResponse = await(wsClient.url(s"$toggleEndpointURL/$toggleId").get)

      // verify
      verifyResponseIsOk(createResponse)

      val maybeToggle = Json.parse(getResponse.body).asOpt(ToggleController.toggleFormat)
      maybeToggle mustBe Some(Toggle(toggleId, name, "toggle description", Map("team" -> "Toguru team")))
    }

    "create a global rollout condition" in {
      // prepare
      val body = """{"percentage": 55}"""

      // execute
      val createResponse = await(wsClient.url(globalRolloutEndpoint).put(body))

      // verify
      verifyResponseIsOk(createResponse)

      fetchToggle().rolloutPercentage mustBe Some(55)
    }

    "update a global rollout condition" in {
      // prepare
      val body = """{"percentage": 42}"""

      // execute
      val updateResponse = await(wsClient.url(globalRolloutEndpoint).put(body))

      // verify
      verifyResponseIsOk(updateResponse)

      fetchToggle().rolloutPercentage mustBe Some(42)
    }

    "delete a global rollout condition" in {
      // execute
      val createResponse = await(wsClient.url(globalRolloutEndpoint).delete())

      // verify
      verifyResponseIsOk(createResponse)

      fetchToggle().rolloutPercentage mustBe None
    }

    "return current toggle state" in {
      // prepare
      await(wsClient.url(toggleEndpointURL).post(toggleAsString("toggle 2")))

      val actor = app.injector.instanceOf[ActorRef](namedKey(classOf[ActorRef], "toggle-state"))

      waitFor(30) {
        val toggles = await((actor ? GetState).mapTo[Map[_,_]])
        toggles.size == 2
      }

      // execute
      val response = await(wsClient.url(toggleStateEndpoint).get())

      // verify
      response.status mustBe OK

      val json = Json.parse(response.body)
      json mustBe a[JsArray]
      json.asInstanceOf[JsArray].value.size == 2
    }

    "return current audit log" in {

      val actor = app.injector.instanceOf[ActorRef](namedKey(classOf[ActorRef], "audit-log"))

      waitFor(30) {
        val log = await((actor ? GetLog).mapTo[Seq[_]])
        log.size == 5
      }

      // execute
      val response = await(wsClient.url(auditLogEndpoint).get())

      // verify
      response.status mustBe OK

      val json = Json.parse(response.body)
      json mustBe a[JsArray]
      json.asInstanceOf[JsArray].value.size == 5
    }

    "allow to update toggle" in {
      // execute
      val response = await(wsClient.url(toggleUpdateEndpoint).put(toggleAsString("toggle 3")))

      // verify
      verifyResponseIsOk(response)
    }
  }

  def namedKey[T](clazz: Class[T], name: String): BindingKey[T] =
    new BindingKey(clazz, Some(QualifierInstance(new NamedImpl(name))))

  /**
    *
    * @param times how many times we want to try.
    * @param test returns true if test (finally) succeeded, false if we need to retry
    */
  def waitFor(times: Int, wait: FiniteDuration = 1.second)(test: => Boolean): Unit = {
    val success = (1 to times).exists { i =>
      if(test) {
        true
      } else {
        if(i < times)
          Thread.sleep(wait.toMillis)
        false
      }
    }

    success mustBe true
  }

  def verifyResponseIsOk(createResponse: WSResponse): Unit = {
    createResponse.status mustBe OK
    val json = Json.parse(createResponse.body)
    (json \ "status").asOpt[String] mustBe Some("Ok")
  }
}
