package toguru.toggles

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl._
import toguru.events.toggles._
import toguru.toggles.Authentication.ApiKeyPrincipal
import toguru.toggles.ToggleActor._

import scala.collection.immutable.Seq

class ToggleActorSpec extends ActorSpec {

  trait ToggleActorSetup {

    val testUser = "test-user"

    def authenticated[T](command: T) = AuthenticatedCommand(command, ApiKeyPrincipal(testUser))

    val toggleId = "toggle-1"
    val toggle = Toggle(toggleId, "name","description")
    val createCmd = CreateToggleCommand(toggle.name, toggle.description, toggle.tags)
    val updateCmd = UpdateToggleCommand(None, Some("new description"), Some(Map("services" -> "toguru")))
    val setCmd = SetGlobalRolloutCommand(42)
    val create = authenticated(createCmd)
    val update = authenticated(updateCmd)
    val delete = authenticated(DeleteToggleCommand)
    val setGlobalRollout = authenticated(setCmd)
    val deleteRollout = authenticated(DeleteGlobalRolloutCommand)

    def createActor(toggle: Option[Toggle] = None) = system.actorOf(Props(new ToggleActor(toggleId, toggle)))

    def fetchToggle(actor: ActorRef): Toggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get
  }

  "actor" should {
    "create toggle when receiving command in initial state" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? create)
      response mustBe CreateSucceeded(toggleId)

      fetchToggle(actor) mustBe toggle
    }

    "reject create command when toggle exists" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? create)
      response mustBe ToggleAlreadyExists(toggleId)
    }

    "reject create command when authentication is missing" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? createCmd)
      response mustBe AuthenticationMissing
    }

    "update toggle when toggle exists" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))

      val response = await(actor ? update)
      response mustBe Success

      fetchToggle(actor) mustBe Toggle(toggleId, toggle.name, updateCmd.description.get, updateCmd.tags.get)
    }

    "reject update when toggle does not exist" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? update)
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "reject update when authentication is missing" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? updateCmd)
      response mustBe AuthenticationMissing
    }

    "delete toggle when toggle exists" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? delete)
      response mustBe Success

      await(actor ? GetToggle) mustBe None
    }

    "reject delete when toggle does not exist" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? delete)
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "create global rollout condition when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? setGlobalRollout)
      response mustBe Success

      val actorToggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get
      actorToggle.rolloutPercentage mustBe Some(setCmd.percentage)
    }

    "update global rollout condition when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle.copy(rolloutPercentage = Some(55))))
      val response = await(actor ? setGlobalRollout)
      response mustBe Success

      fetchToggle(actor).rolloutPercentage mustBe Some(setCmd.percentage)
    }

    "reject set global rollout condition command when toggle does not exists" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? setGlobalRollout)
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "delete rollout condition when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle.copy(rolloutPercentage = Some(42))))
      val response = await(actor ? deleteRollout)
      response mustBe Success

      fetchToggle(actor).rolloutPercentage mustBe None
    }

    "return success on delete when rollout condition does not exist" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? deleteRollout)
      response mustBe Success
    }

    "persist toggle events" in new ToggleActorSetup {
      val actor = system.actorOf(Props(new ToggleActor(toggleId, None) {
        override def time = 0
      }))

      lazy val readJournal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
        .asInstanceOf[ReadJournal with CurrentEventsByPersistenceIdQuery]

      actor ? create
      actor ? setGlobalRollout
      actor ? authenticated(SetGlobalRolloutCommand(55))
      await(actor ? deleteRollout)

      val eventualEnvelopes = readJournal.currentEventsByPersistenceId(toggleId, 0, 100).runWith(Sink.seq)
      val events = await(eventualEnvelopes).map(_.event)
      val meta = Some(Metadata(0, testUser))
      events mustBe Seq(
        ToggleCreated(toggle.name, toggle.description, toggle.tags, meta),
        GlobalRolloutCreated(setCmd.percentage, meta),
        GlobalRolloutUpdated(55, meta),
        GlobalRolloutDeleted(meta)
      )
    }
  }
}
