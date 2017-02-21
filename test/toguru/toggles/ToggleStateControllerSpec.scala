package toguru.toggles

import akka.actor.{Actor, ActorSystem, Props}
import com.codahale.metrics.Counter
import com.typesafe.config.{Config => TypesafeConfig}
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import toguru.app.Config
import toguru.helpers.ControllerSpec
import toguru.toggles.ToggleStateActor.{GetState, ToggleStateInitializing}
import toguru.toggles.events.Rollout

import scala.concurrent.duration._


object ToggleStateControllerSpec {
  implicit class MyFakeRequest[A](val request: FakeRequest[A]) extends AnyVal {
    def withAccept(mimeType: String) = request.withHeaders(HeaderNames.ACCEPT -> mimeType)
  }

  case class ToggleStatesV2(sequenceNo: Long, toggles: Seq[ToggleStateV2])

  def stateV2(s: ToggleState) =
    ToggleStateV2(s.id, s.tags, s.activations.headOption.flatMap(_.rollout.map(_.percentage)))

  case class ToggleStateV2(id: String,
                           tags: Map[String, String] = Map.empty,
                           rolloutPercentage: Option[Int] = None)
}

class ToggleStateControllerSpec extends ControllerSpec {
  import ToggleStateControllerSpec._


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
    "toggle-3" -> ToggleState("toggle-3",
      activations = IndexedSeq(ToggleActivation(Map("country" -> Seq("de-DE", "de-AT")), Some(Rollout(25))))),
    "toggle-2" -> ToggleState("toggle-2"),
    "toggle-1" -> ToggleState("toggle-1", Map("team" -> "Toguru team"))
  )

  def  toggleStateActorProps(toggles: Map[String,ToggleState]) =
    Props(new Actor() { override def receive = { case GetState => sender ! ToggleStates(10, toggles.values.to[Vector].sortBy(_.id))}})

  "get method" should {
    "return current toggle state" in {
      // prepare
      implicit val rolloutReads = Json.reads[Rollout]
      implicit val activationReads = Json.reads[ToggleActivation]
      implicit val toggleReads = Json.reads[ToggleState]
      implicit val reads = Json.reads[ToggleStates]

      val controller: ToggleStateController = createController(toggleStateActorProps(toggles))

      val request = FakeRequest().withAccept(ToggleStateController.MimeApiV3)

      // execute
      val result = controller.get(Some(9)).apply(request)

      // verify
      status(result) mustBe 200
      val states = contentAsJson(result).as[ToggleStates]

      states.toggles mustBe Seq(
        toggles("toggle-1"),
        toggles("toggle-2"),
        toggles("toggle-3")
      )
    }

    "return version specific format V2" in {
      // prepare
      implicit val toggleV2Reads = Json.reads[ToggleStateV2]
      val togglesV2Reads = Json.reads[ToggleStatesV2]

      val controller: ToggleStateController = createController(toggleStateActorProps(toggles))

      val request = FakeRequest().withAccept(ToggleStateController.MimeApiV2)

      // execute
      val result = controller.get(Some(9)).apply(request)

      // verify
      status(result) mustBe 200
      val states = contentAsJson(result).as(togglesV2Reads)

      states.toggles mustBe Seq(
        ToggleStateV2("toggle-1", Map("team" -> "Toguru team")),
        ToggleStateV2("toggle-2"),
        ToggleStateV2("toggle-3", rolloutPercentage = Some(25))
      )
    }

    "return format V1 if requested" in {
      // prepare
      implicit val reads = Json.reads[ToggleStateV2]

      val controller: ToggleStateController = createController(toggleStateActorProps(toggles))

      val request = FakeRequest()

      // execute
      val result = controller.get(None).apply(request)

      // verify
      status(result) mustBe 200

      val state = contentAsJson(result).as[Seq[ToggleStateV2]]

      val expectedToggles = (1 to 3).map(i => stateV2(toggles(s"toggle-$i")))

      state mustBe expectedToggles
    }


    "reject unknown toggle format requests" in {
      val MimeApiV4 = "application/vnd.toguru.v4+json"

      val controller: ToggleStateController = createController(toggleStateActorProps(toggles))

      val request = FakeRequest().withAccept(MimeApiV4)

      // execute
      val result = controller.get(None).apply(request)

      // verify
      status(result) mustBe 406

      val responseBody = contentAsJson(result)

      val maybeAllowedContentTypes = (responseBody \ "allowedContentTypes").asOpt[Seq[String]]

      maybeAllowedContentTypes mustBe defined

      maybeAllowedContentTypes.value mustNot be (empty)
    }

    "return Internal Server Error if seqNo is newer than server seqNo" in {
      // prepare
      val controller: ToggleStateController = createController(toggleStateActorProps(toggles))

      val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> ToggleStateController.MimeApiV2)

      // execute
      val result = controller.get(Some(11)).apply(request)

      // verify
      status(result) mustBe 500
    }

    "return Internal Server Error if toggle state actor responds with initializing" in {
      // prepare
      val initializingActor = Props(new Actor() { override def receive = { case GetState => sender ! ToggleStateInitializing }})
      val controller: ToggleStateController = createController(initializingActor)

      val request = FakeRequest().withHeaders(HeaderNames.ACCEPT -> ToggleStateController.MimeApiV2)

      // execute
      val result = controller.get(Some(10)).apply(request)

      // verify
      status(result) mustBe 500
    }
  }
}
