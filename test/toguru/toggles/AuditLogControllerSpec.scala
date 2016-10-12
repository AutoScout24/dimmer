package toguru.toggles

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.{Config => TypesafeConfig}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, Json}
import play.api.test.Helpers._
import toguru.app.Config
import toguru.events.toggles.{GlobalRolloutCreated, ToggleCreated}
import toguru.helpers.AuthorizationHelpers
import toguru.toggles.AuditLogActor.GetLog

import scala.concurrent.duration._


class AuditLogControllerSpec extends PlaySpec with MockitoSugar with AuthorizationHelpers {

  def createController(props: Props): AuditLogController = {
    val config = new Config {
      override val actorTimeout = 100.millis
      override val typesafeConfig = mock[TypesafeConfig]
      override def auth = authConfig
    }

    val system = ActorSystem()
    val actor = system.actorOf(props)

    new AuditLogController(actor, config)
  }

  "get method" should {
    "return list of toggle states fetched from actor" in {
      // prepare
      val tags =  Map("team" -> "Toguru team")
      val events = List(
        ("toggle-1", GlobalRolloutCreated(20)),
        ("toggle-1", ToggleCreated("toggle 1", "first toggle", tags))
      )

      implicit val reads = Json.reads[ToggleState]

      val controller = createController(Props(new Actor() {
        override def receive = { case GetLog => sender ! events }
      }))

      // execute
      val result = controller.get().apply(authorizedRequest)

      // verify
      status(result) mustBe 200
      val log = contentAsJson(result)

      log mustBe a[JsArray]

      (log(0) \ "id").asOpt[String] mustBe Some("toggle-1")
      (log(0) \ "percentage").asOpt[Int] mustBe Some(20)

      (log(1) \ "id").asOpt[String] mustBe Some("toggle-1")
      (log(1) \ "name").asOpt[String] mustBe Some("toggle 1")
      (log(1) \ "description").asOpt[String] mustBe Some("first toggle")
      (log(1) \ "tags").asOpt[Map[String,String]] mustBe Some(tags)
    }
  }
}
