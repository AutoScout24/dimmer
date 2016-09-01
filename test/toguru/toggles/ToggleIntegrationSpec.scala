package toguru.toggles

import toguru.PostgresSetup
import toguru.app.Config
import toguru.toggles.ToggleActor.GetToggle
import play.api.test.Helpers._
import org.scalatest.BeforeAndAfterAll
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.api.mvc.Results
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}


class ToggleIntegrationSpec extends PlaySpec
  with BeforeAndAfterAll with Results with PostgresSetup with OneServerPerSuite with FutureAwaits with DefaultAwaitTimeout {

  override def config = app.injector.instanceOf[Config].typesafeConfig

  override def log(message: String): Unit = info(message)

  override protected def beforeAll(): Unit = startPostgres()

  override protected def afterAll(): Unit = stopPostgres()

  def getToggle(name: String): Option[Toggle] = {
    import akka.pattern.ask

    val actor = ToggleActor.provider(app.actorSystem).create(ToggleActor.toId(name))
    await((actor ? GetToggle).mapTo[Option[Toggle]])
  }

  def toggleAsString(name: String) =
    s"""{"name" : "$name", "description" : "toggle description", "tags" : {"team" : "Shared Services"}}"""

  "ToggleEndpoint" should {
    "successfully create toggles" in {
      // prepare
      waitForPostgres()

      val name = "toggle name"
      val wsClient = app.injector.instanceOf[WSClient]
      val toggleEndpointURL = s"http://localhost:$port/toggle"
      val body = toggleAsString(name)

      // execute
      val createResponse = await(wsClient.url(toggleEndpointURL).post(body))
      val getResponse = await(wsClient.url(s"$toggleEndpointURL/toggle-name").get)


      // verify
      createResponse.status mustBe OK
      val json = Json.parse(createResponse.body)

      (json \ "status").asOpt[String] mustBe Some("Ok")

      getResponse.status mustBe OK

      val maybeToggle = Json.parse(getResponse.body).asOpt(ToggleController.toggleReads)
      maybeToggle mustBe Some(Toggle("toggle-name", "toggle name", "toggle description", Map("team" -> "Shared Services")))
    }

    "reject creating duplicate toggles" in {
      // prepare
      waitForPostgres()

      val name = "toggle name 2"
      val wsClient = app.injector.instanceOf[WSClient]
      val toggleEndpointURL = s"http://localhost:$port/toggle"
      val body = toggleAsString(name)

      // fist request.
      await(wsClient.url(toggleEndpointURL).post(body))

      // execute
      // second request.
      val response = await(wsClient.url(toggleEndpointURL).post(body))

      response.status mustBe CONFLICT
      val json = Json.parse(response.body)
      (json \ "status").asOpt[String] mustBe Some("Conflict")
    }
  }

  "RolloutEndpoint" should {
    "successfully create global rollout condition" in {
      // prepare
      waitForPostgres()

      val name = "create global rollout toggle"
      val id = ToggleActor.toId(name)
      val wsClient = app.injector.instanceOf[WSClient]
      val toggleEndpointURL = s"http://localhost:$port/toggle"
      val globalRolloutEndpoint = s"http://localhost:$port/toggle/$id/globalrollout"
      val body = toggleAsString(name)
      await(wsClient.url(toggleEndpointURL).post(body))

      // execute
      val createResponse = await(wsClient.url(globalRolloutEndpoint).post("""{"percentage": 55}"""))


      // verify
      val createJson = Json.parse(createResponse.body)
      (createJson \ "status").asOpt[String] mustBe Some("Ok")
      createResponse.status mustBe OK

      val getResponse = await(wsClient.url(s"$toggleEndpointURL/$id").get)
      val maybeToggle = Json.parse(getResponse.body).asOpt(ToggleController.toggleReads)

      maybeToggle mustBe a[Some[_]]
      maybeToggle.get.rolloutPercentage mustBe Some(55)
    }
  }
}
