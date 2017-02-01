package toguru.toggles

import akka.actor.{Actor, ActorSystem, Props}
import com.codahale.metrics.Counter
import com.typesafe.config.{Config => TypesafeConfig}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import toguru.app.Config
import toguru.toggles.ToggleStateActor.{GetState, ToggleStateInitializing}

import scala.concurrent.duration._

class ToggleStateControllerSpec extends PlaySpec with MockitoSugar {

  def createController(props: Props): ToggleStateController = {
    val config = new Config {
      override val actorTimeout = 100.millis
      override val typesafeConfig = mock[TypesafeConfig]
      override def auth = Authentication.Config(Seq.empty, disabled = false)
      override def auditLog = AuditLog.Config()
      override def toggleState = ToggleState.Config()
    }

    val system = ActorSystem()
    val actor = system.actorOf(props)
    val counter = mock[Counter]

    new ToggleStateController(actor, config, counter, counter)
  }

  val toggles = Map(
    "toggle-2" -> ToggleState("toggle-2", rolloutPercentage = Some(20)),
    "toggle-1" -> ToggleState("toggle-1", Map("team" -> "Toguru team"))
  )

  def  toggleStateActorProps(toggles: Map[String,ToggleState]) =
    Props(new Actor() { override def receive = { case GetState => sender ! ToggleStates(10, toggles.values.to[Vector].sortBy(_.id))}})

  "get method" should {
    "return list of toggle states fetched from actor" in {
      // prepare
      implicit val reads = Json.reads[ToggleState]

      val controller: ToggleStateController = createController(toggleStateActorProps(toggles))

      val request = FakeRequest()

      // execute
      val result = controller.get(None).apply(request)

      // verify
      status(result) mustBe 200
      val state = contentAsJson(result).as[Seq[ToggleState]]

      state mustBe Seq(
        toggles("toggle-1"),
        toggles("toggle-2")
      )
    }

    "return version specific format" in {
      // prepare
      implicit val toggleReads = Json.reads[ToggleState]

      implicit val reads = Json.reads[ToggleStates]

      val controller: ToggleStateController = createController(toggleStateActorProps(toggles))

      val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> ToggleStateController.MimeApiV2)

      // execute
      val result = controller.get(Some(9)).apply(request)

      // verify
      status(result) mustBe 200
      val states = contentAsJson(result).as[ToggleStates]

      states.toggles mustBe Seq(
        toggles("toggle-1"),
        toggles("toggle-2")
      )
    }

    "return Internal Server Error if seqNo is newer than server seqNo" in {
      // prepare
      val controller: ToggleStateController = createController(toggleStateActorProps(toggles))

      val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> ToggleStateController.MimeApiV2)

      // execute
      val result = controller.get(Some(11)).apply(request)

      status(result) mustBe 500
    }

    "return Internal Server Error if toggle state actor responds with initializing" in {
      // prepare
      val initializingActor = Props(new Actor() { override def receive = { case GetState => sender ! ToggleStateInitializing }})
      val controller: ToggleStateController = createController(initializingActor)

      val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> ToggleStateController.MimeApiV2)

      // execute
      val result = controller.get(Some(10)).apply(request)

      status(result) mustBe 500
    }
  }
}
