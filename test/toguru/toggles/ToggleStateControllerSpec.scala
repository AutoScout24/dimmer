package toguru.toggles

import akka.actor.{Actor, ActorSystem, Props}
import com.typesafe.config.{Config => TypesafeConfig}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import toguru.app.Config
import toguru.toggles.ToggleStateActor.GetState

import scala.concurrent.duration._


class ToggleStateControllerSpec extends PlaySpec with MockitoSugar {

  def createController(props: Props): ToggleStateController = {
    val config = new Config {
      override val actorTimeout = 100.millis
      override val typesafeConfig = mock[TypesafeConfig]
    }

    val system = ActorSystem()
    val actor = system.actorOf(props)

    new ToggleStateController(actor, config)
  }

  "get method" should {
    "return list of toggle states fetched from actor" in {
      // prepare
      val toggles = Map(
        "toggle-2" -> ToggleState("toggle-2", rolloutPercentage = Some(20)),
        "toggle-1" -> ToggleState("toggle-1", Map("team" -> "Toguru team"))
      )

      implicit val reads = Json.reads[ToggleState]

      val controller: ToggleStateController = createController(Props(new Actor() {
        override def receive = { case GetState => sender ! toggles }
      }))

      val request = FakeRequest()


      // execute
      val result = controller.get().apply(request)

      // verify
      status(result) mustBe 200
      val state = contentAsJson(result).as[Seq[ToggleState]]

      state mustBe Seq(
        toggles("toggle-1"),
        toggles("toggle-2")
      )
    }
  }
}
