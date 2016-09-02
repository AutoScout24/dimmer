package toguru.toggles

import toguru.PostgresSetup
import toguru.app.Config
import toguru.toggles.ToggleActor.GetToggle
import play.api.test.Helpers._
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.Results
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}


class ToggleIntegrationSpec extends PlaySpec
  with BeforeAndAfterAll with Results with PostgresSetup with OneServerPerSuite with FutureAwaits with DefaultAwaitTimeout {

  override def config = app.injector.instanceOf[Config].typesafeConfig

  override def log(message: String): Unit = info(message)

  override protected def beforeAll(): Unit = startPostgres()

  override protected def afterAll(): Unit = stopPostgres()



  def toggleAsString(name: String) =
    s"""{"name" : "$name", "description" : "toggle description", "tags" : {"team" : "Shared Services"}}"""

  "Toggle API" should {
    val name = "toggle name"
    val toggleId = ToggleActor.toId(name)
    val toggleEndpointURL = s"http://localhost:$port/toggle"
    val globalRolloutEndpoint = s"http://localhost:$port/toggle/$toggleId/globalrollout"
    val wsClient = app.injector.instanceOf[WSClient]

    def getToggle(): Toggle = {
      val getResponse = await(wsClient.url(s"$toggleEndpointURL/$toggleId").get)
      Json.parse(getResponse.body).as(ToggleController.toggleReads)
    }

    "create a toggle" in {
      // prepare
      waitForPostgres()

      val body = toggleAsString(name)
      // execute
      val createResponse = await(wsClient.url(toggleEndpointURL).post(body))
      val getResponse = await(wsClient.url(s"$toggleEndpointURL/toggle-name").get)

      // verify
      verifyResponseIsOk(createResponse)

      val maybeToggle = Json.parse(getResponse.body).asOpt(ToggleController.toggleReads)
      maybeToggle mustBe Some(Toggle("toggle-name", "toggle name", "toggle description", Map("team" -> "Shared Services")))
    }

    "create a global rollout condition" in {
      // prepare
      val body = """{"percentage": 55}"""

      // execute
      val createResponse = await(wsClient.url(globalRolloutEndpoint).post(body))

      // verify
      verifyResponseIsOk(createResponse)

      getToggle().rolloutPercentage mustBe Some(55)
    }

    "update a global rollout condition" in {
      // prepare
      val body = """{"percentage": 42}"""

      // execute
      val updateResponse = await(wsClient.url(globalRolloutEndpoint).put("""{"percentage": 42}"""))

      // verify
      verifyResponseIsOk(updateResponse)

      getToggle().rolloutPercentage mustBe Some(42)
    }
  }


  def verifyResponseIsOk(createResponse: WSResponse): Unit = {
    createResponse.status mustBe OK
    val json = Json.parse(createResponse.body)
    (json \ "status").asOpt[String] mustBe Some("Ok")
  }
}
