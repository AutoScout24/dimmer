package toguru.toggles

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.persistence.query.{EventEnvelope, PersistenceQuery}
import toguru.toggles.ToggleActor._
import toguru.events.toggles._

import scala.collection.immutable.Seq

class ToggleActorSpec extends ActorSpec {

  trait ToggleActorSetup {
    val toggleId = "toggle-1"
    val toggle = Toggle(toggleId, "name","description")
    val createCommand = CreateToggleCommand(toggle.name, toggle.description, toggle.tags)
    val updateCommand = UpdateToggleCommand(None, Some("new description"), Some(Map("services" -> "toguru")))
    val deleteCommand = DeleteToggleCommand(Some(true))
    val setGlobalRolloutCommand = SetGlobalRolloutCommand(42)

    def createActor(toggle: Option[Toggle] = None) = system.actorOf(Props(new ToggleActor(toggleId, toggle)))

    def fetchToggle(actor: ActorRef): Toggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get
  }

  "actor" should {
    "create toggle when receiving command in initial state" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? createCommand)
      response mustBe CreateSucceeded(toggleId)

      fetchToggle(actor) mustBe toggle
    }

    "reject create command when toggle exists" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? createCommand)
      response mustBe ToggleAlreadyExists(toggleId)
    }

    "update toggle when toggle exists" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? updateCommand)
      response mustBe Success

      fetchToggle(actor) mustBe Toggle(toggleId, toggle.name, updateCommand.description.get, updateCommand.tags.get)
    }

    "reject update when toggle does not exist" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? updateCommand)
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "delete toggle when toggle exists and command confirms delete" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? deleteCommand)
      response mustBe Success

      await(actor ? GetToggle) mustBe None
    }

    "reject delete when toggle does not exist" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? deleteCommand)
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "reject delete when command does not confirm delete" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? DeleteToggleCommand(None))
      response mustBe UnconfirmedDelete
    }

    "reply that it does not exist when command does not confirm delete but toggle does not exist anyway" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? DeleteToggleCommand(None))
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "create global rollout condition when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? setGlobalRolloutCommand)
      response mustBe Success

      val actorToggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get
      actorToggle.rolloutPercentage mustBe Some(setGlobalRolloutCommand.percentage)
    }

    "update global rollout condition when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle.copy(rolloutPercentage = Some(55))))
      val response = await(actor ? setGlobalRolloutCommand)
      response mustBe Success

      fetchToggle(actor).rolloutPercentage mustBe Some(setGlobalRolloutCommand.percentage)
    }

    "reject set global rollout condition command when toggle does not exists" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? setGlobalRolloutCommand)
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "delete rollout condition when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle.copy(rolloutPercentage = Some(42))))
      val response = await(actor ? DeleteGlobalRolloutCommand)
      response mustBe Success

      fetchToggle(actor).rolloutPercentage mustBe None
    }

    "return success on delete when rollout condition does not exist" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? DeleteGlobalRolloutCommand)
      response mustBe Success
    }

    "persist toggle events" in new ToggleActorSetup {
      val actor = system.actorOf(Props(new ToggleActor(toggleId, None) {
        override def time = 0
      }))

      import akka.persistence.query.scaladsl._
      import akka.stream.scaladsl._
      lazy val readJournal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
        .asInstanceOf[ReadJournal with CurrentEventsByPersistenceIdQuery]

      actor ? createCommand
      actor ? setGlobalRolloutCommand
      actor ? SetGlobalRolloutCommand(55)
      await(actor ? DeleteGlobalRolloutCommand)

      val eventualEnvelopes = readJournal.currentEventsByPersistenceId(toggleId, 0, 100).runWith(Sink.seq)
      val events = await(eventualEnvelopes).map(_.event)
      val meta = Some(Metadata(0, ""))
      events mustBe Seq(
        ToggleCreated(toggle.name, toggle.description, toggle.tags, meta),
        GlobalRolloutCreated(setGlobalRolloutCommand.percentage, meta),
        GlobalRolloutUpdated(55, meta),
        GlobalRolloutDeleted(meta)
      )
    }
  }
}
