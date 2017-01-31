package toguru.toggles

import akka.pattern.ask
import akka.actor.{ActorRef, Props}
import akka.persistence.query.EventEnvelope
import toguru.toggles.ToggleStateActor.{GetState, ToggleStateInitializing}
import toguru.toggles.events._

import scala.concurrent.Future
import scala.concurrent.duration._

class ToggleStateActorSpec extends ActorSpec with WaitFor {

  def createActor(toggles: Map[String, ToggleState] = Map.empty, maxSequenceNo: Long = 0, initialize: Boolean = false): ActorRef =
    system.actorOf(Props(new ToggleStateActor((_,_) => (), (_,_) => Future.successful(maxSequenceNo), ToggleState.Config(initialize), toggles)))

  def event(id: String, event: ToggleEvent) = EventEnvelope(0, id, 0, event)

  val toggles = Map(
    "toggle-1" -> ToggleState("toggle-1", Map("team" -> "Toguru team")),
    "toggle-2" -> ToggleState("toggle-2", rolloutPercentage = Some(20))
  )

  "toggle state actor" should {
    "return current toggle state" in {
      val actor = createActor(toggles)

      val response = await(actor ? GetState)

      response mustBe ToggleStates(0, toggles.values.to[Seq])
    }

    "build toggle state from events" in {
      val actor = createActor()

      val id1 = "toggle-1"
      val id2 = "toggle-2"
      val id3 = "toggle-3"

      actor ! event(id1, ToggleCreated("name", "description", Map("team" -> "Toguru team")))
      actor ! event(id1, GlobalRolloutCreated(10))
      actor ! event(id1, GlobalRolloutDeleted())

      actor ! event(id2, ToggleCreated("name", "", Map.empty))
      actor ! event(id2, ToggleUpdated("name", "description", Map.empty))
      actor ! event(id2, GlobalRolloutCreated(10))
      actor ! event(id2, GlobalRolloutUpdated(20))

      actor ! event(id3, ToggleCreated("name", "description", Map.empty))
      actor ! event(id3, ToggleDeleted())

      val response = await(actor ? GetState)

      response mustBe ToggleStates(0, toggles.values.to[Seq])
    }

    "returns correct sequence number" in {
      val actor = createActor()

      actor ! EventEnvelope(10, "toggle-1", 0, ToggleCreated("name", "description", Map("team" -> "Toguru team")))

      val response = await(actor ? GetState)

      response mustBe ToggleStates(10, Seq(ToggleState("toggle-1", Map("team" -> "Toguru team"))))
    }

    "start in initializing state" in {
      val actor = createActor(maxSequenceNo = 10, initialize = true)

      val response = await(actor ? GetState)

      response mustBe ToggleStateInitializing
    }

    "switch to initialized state after replaying all events" in {
      val actor = createActor(maxSequenceNo = 10, initialize = true)

      actor ! EventEnvelope(10, "toggle-1", 0, ToggleCreated("name", "", Map.empty))

      waitFor(10.seconds) {
        await(actor ? GetState) != ToggleStateInitializing
      }

      val response = await(actor ? GetState)

      response mustBe ToggleStates(10, Seq(ToggleState("toggle-1", Map.empty)))
    }
  }
}

