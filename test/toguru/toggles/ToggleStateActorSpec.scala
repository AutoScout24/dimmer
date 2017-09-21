package toguru.toggles

import akka.pattern.ask
import akka.actor.{ActorRef, Props}
import akka.persistence.query.EventEnvelope
import toguru.helpers.ActorSpec
import toguru.toggles.ToggleStateActor.{GetSeqNo, GetState, ToggleStateInitializing}
import toguru.toggles.events._

import scala.concurrent.Future
import scala.concurrent.duration._

class ToggleStateActorSpec extends ActorSpec with WaitFor {

  def createActor(toggles: Map[String, ToggleState] = Map.empty, maxSequenceNo: Long = 0, initialize: Boolean = false): ActorRef =
    system.actorOf(Props(new ToggleStateActor((_,_) => (), (_,_) => Future.successful(maxSequenceNo), ToggleState.Config(initialize), toggles)))

  def rollout(p: Int) = Some(Rollout(p))

  def event(id: String, event: ToggleEvent) = EventEnvelope(0, id, 0, event)

  val toggles = Map(
    "toggle-1" -> ToggleState("toggle-1", Map("team" -> "Toguru team")),
    "toggle-2" -> ToggleState("toggle-2", activations = IndexedSeq(ToggleActivation(rollout = Some(Rollout(20)))))
  )

  "toggle state actor" should {
    "return current toggle state" in {
      // prepare
      val actor = createActor(toggles)

      // execute
      val response = await(actor ? GetState)

      // verify
      response mustBe ToggleStates(0, toggles.values.to[Seq])
    }

    "build toggle state from events" in {
      // prepare
      val actor = createActor()

      val id1 = "toggle-1"
      val id2 = "toggle-2"
      val id3 = "toggle-3"

      // execute
      actor ! event(id1, ToggleCreated("name", "description", Map("team" -> "Toguru team")))
      actor ! event(id1, ActivationCreated(index = 0, rollout = rollout(10)))
      actor ! event(id1, ActivationDeleted(index = 0))

      actor ! event(id2, ToggleCreated("name", "", Map.empty))
      actor ! event(id2, ToggleUpdated("name", "description", Map.empty))
      actor ! event(id2, ActivationCreated(index = 0, rollout = rollout(10)))
      actor ! event(id2, ActivationUpdated(index = 0, rollout = rollout(20)))

      actor ! event(id3, ToggleCreated("name", "description", Map.empty))
      actor ! event(id3, ToggleDeleted())

      // verify
      val response = await(actor ? GetState)
      response mustBe ToggleStates(0, toggles.values.to[Seq])
    }

    "filters out activations with rollout 0" in {
      // prepare
      val actor = createActor()
      val id = "toggle-1"
      val id2 = "toggle-2"

      // execute
      actor ! event(id, ToggleCreated("name", "description", Map.empty))
      actor ! event(id, ActivationCreated(index = 0, rollout = rollout(10)))
      actor ! event(id, ActivationUpdated(index = 0, rollout = rollout(0)))

      actor ! event(id2, ToggleCreated("name", "description", Map.empty))
      actor ! event(id2, ActivationCreated(index = 0, rollout = rollout(0)))

      val response = await(actor ? GetState)
      response mustBe ToggleStates(0, Seq(
        ToggleState(id = id, activations = IndexedSeq.empty),
        ToggleState(id = id2, activations = IndexedSeq.empty)
      ))
    }

    "returns correct sequence number" in {
      // prepare
      val actor = createActor()
      actor ! EventEnvelope(10, "toggle-1", 0, ToggleCreated("name", "description", Map("team" -> "Toguru team")))

      // execute
      val response = await(actor ? GetState)

      // verify
      response mustBe ToggleStates(10, Seq(ToggleState("toggle-1", Map("team" -> "Toguru team"))))
    }

    "returns correct sequence number when receiving GetSeqNo" in {
      // prepare
      val actor = createActor()
      actor ! EventEnvelope(10, "toggle-1", 0, ToggleCreated("name", "description", Map("team" -> "Toguru team")))

      // execute
      val response = await(actor ? GetSeqNo)

      // verify
      response mustBe 10
    }

    "start in initializing state" in {
      // prepare
      val actor = createActor(maxSequenceNo = 10, initialize = true)

      // execute
      val getStateResponse = await(actor ? GetState)
      val getSeqNoResponse = await(actor ? GetSeqNo)

      // verify
      getStateResponse mustBe ToggleStateInitializing
      getSeqNoResponse mustBe ToggleStateInitializing
    }

    "switch to initialized state after replaying all events" in {
      // prepare
      val actor = createActor(maxSequenceNo = 10, initialize = true)
      actor ! EventEnvelope(10, "toggle-1", 0, ToggleCreated("name", "", Map.empty))

      // execute
      waitFor(10.seconds) { await(actor ? GetState) != ToggleStateInitializing }

      // verify
      val response = await(actor ? GetState)
      response mustBe ToggleStates(10, Seq(ToggleState("toggle-1", Map.empty)))
    }
  }
}

