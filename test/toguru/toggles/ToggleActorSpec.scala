package toguru.toggles

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.persistence.inmemory.extension.InMemorySnapshotStorage.SnapshotForMaxSequenceNr
import akka.persistence.inmemory.extension.StorageExtension
import akka.persistence.inmemory.snapshotEntry
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl._
import toguru.helpers.ActorSpec
import toguru.toggles.Authentication.ApiKeyPrincipal
import toguru.toggles.ToggleActor._
import toguru.toggles.events._

import scala.collection.immutable.Seq
import scala.concurrent.duration._

class ToggleActorSpec extends ActorSpec with WaitFor {

  trait ToggleActorSetup {

    val testUser = "test-user"

    def authenticated[T](command: T) = AuthenticatedCommand(command, ApiKeyPrincipal(testUser))

    val snapshotTimeout = 10.seconds

    val toggleId = "toggle-1"
    val toggle = Toggle(toggleId, "name","description")
    val createCmd = CreateToggleCommand(toggle.name, toggle.description, toggle.tags)
    val updateCmd = UpdateToggleCommand(None, Some("new description"), Some(Map("services" -> "toguru")))
    val createActivationCmd = CreateActivationCommand(percentage = Some(Rollout(42)))
    val create = authenticated(createCmd)
    val update = authenticated(updateCmd)
    val delete = authenticated(DeleteToggleCommand)
    val createActivation = authenticated(createActivationCmd)
    val updateActivationCmd = UpdateActivationCommand(0, percentage = Some(Rollout(55)), Map("a" -> Seq("b")))
    val updateActivation = authenticated(updateActivationCmd)
    val deleteActivation = authenticated(DeleteActivationCommand(0))

    def createActor(toggle: Option[Toggle] = None) = system.actorOf(Props(new ToggleActor(toggleId, toggle)))

    def fetchToggle(actor: ActorRef): Toggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get

    def primaryRollout(toggle: Toggle): Option[Rollout] = toggle.activations(0).rollout

    def snapshotSequenceNr(maxSequenceNr: Long = 100): Option[Long] =
      await(StorageExtension(system).snapshotStorage ? SnapshotForMaxSequenceNr(toggleId, maxSequenceNr)).asInstanceOf[Option[snapshotEntry]].map(_.sequenceNumber)
  }

  "actor" should {
    "create toggle when receiving command in initial state" in new ToggleActorSetup {
      // prepare
      val actor = createActor()

      // execute
      val response = await(actor ? create)

      // verify
      response mustBe CreateSucceeded(toggleId)
      fetchToggle(actor) mustBe toggle
    }

    "reject create command when toggle exists" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle))

      // execute
      val response = await(actor ? create)

      // verify
      response mustBe ToggleAlreadyExists(toggleId)
    }

    "reject create command when authentication is missing" in new ToggleActorSetup {
      // prepare
      val actor = createActor()

      // execute
      val response = await(actor ? createCmd)

      // verify
      response mustBe AuthenticationMissing
    }

    "update toggle when toggle exists" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle))

      // execute
      val response = await(actor ? update)

      // verify
      response mustBe Success
      fetchToggle(actor) mustBe Toggle(toggleId, toggle.name, updateCmd.description.get, updateCmd.tags.get)
    }

    "keeps toggle description and tags when updating names" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle))
      override val updateCmd = UpdateToggleCommand(Some("new name"), None, None)

      // execute
      val response = await(actor ? authenticated(updateCmd))

      // verify
      response mustBe Success
      fetchToggle(actor) mustBe Toggle(toggleId, updateCmd.name.get, toggle.description, toggle.tags)
    }

    "reject update when toggle does not exist" in new ToggleActorSetup {
      // prepare
      val actor = createActor()

      // execute
      val response = await(actor ? update)

      // verify
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "reject update when authentication is missing" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle))

      // execute
      val response = await(actor ? updateCmd)

      // verify
      response mustBe AuthenticationMissing
    }

    "delete toggle when toggle exists" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle))

      // execute
      val response = await(actor ? delete)

      // verify
      response mustBe Success
      await(actor ? GetToggle) mustBe None
    }

    "reject delete when toggle does not exist" in new ToggleActorSetup {
      // prepare
      val actor = createActor()

      // execute
      val response = await(actor ? delete)

      // verify
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "allow to re-create toggle after delete" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle.copy(name = "initial toggle name")))
      actor ? delete

      // execute
      val response = await(actor ? create)

      // verify
      response mustBe CreateSucceeded(toggleId)
      fetchToggle(actor) mustBe toggle
    }

    "create activation condition when receiving command" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle))

      // execute
      val response = await(actor ? createActivation)

      // verify
      response mustBe CreateActivationSuccess(0)
      val actorToggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get
      actorToggle.activations(0).rollout mustBe createActivationCmd.percentage
    }

    "update activation condition when receiving command" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle.copy(rolloutPercentage = Some(55))))

      // execute
      val response = await(actor ? updateActivation)

      // verify
      response mustBe Success
      val fToggle = fetchToggle(actor)
      fToggle.activations must have length (1)
      primaryRollout(fToggle) mustBe updateActivation.command.percentage
    }

    "reject set global rollout condition command when toggle does not exists" in new ToggleActorSetup {
      // prepare
      val actor = createActor()

      // execute
      val response = await(actor ? createActivation)

      // verify
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "delete activation when receiving command" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle.copy(rolloutPercentage = Some(42))))

      // execute
      val response = await(actor ? deleteActivation)

      // verify
      response mustBe Success
      fetchToggle(actor).activations mustBe empty
    }

    "return success on delete when rollout condition does not exist" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle))

      // execute
      val response = await(actor ? deleteActivation)

      // verify
      response mustBe Success
    }

    "create activations when receiving command" in new ToggleActorSetup {
      // prepare
      val actor = createActor(Some(toggle))

      // execute
      val response = await(actor ? createActivation)

      // verify
      response mustBe CreateActivationSuccess(0)
      val activation = fetchToggle(actor).activations(0)
      activation mustBe ToggleActivation(createActivationCmd.attributes, createActivationCmd.percentage)
    }

    "persist toggle events" in new ToggleActorSetup {
      // prepare
      val actor = system.actorOf(Props(new ToggleActor(toggleId, None) {
        override def time() = 0
      }))

      lazy val readJournal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
        .asInstanceOf[ReadJournal with CurrentEventsByPersistenceIdQuery]

      // execute
      actor ? create
      actor ? createActivation
      actor ? updateActivation
      await(actor ? deleteActivation)

      // verify
      val eventualEnvelopes = readJournal.currentEventsByPersistenceId(toggleId, 0, 100).runWith(Sink.seq)
      val events = await(eventualEnvelopes).map(_.event)
      val meta = Some(Metadata(0, testUser))

      events(0) mustBe ToggleCreated(toggle.name, toggle.description, toggle.tags, meta)
      events(1) mustBe ActivationCreated(meta, 0, rollout = createActivationCmd.percentage)
      events(2) mustBe ActivationUpdated(meta, 0, toProtoBuf(updateActivationCmd.attributes), updateActivationCmd.percentage)
      events(3) mustBe ActivationDeleted(meta, 0)
    }

    "save snapshots every 10 events" in new ToggleActorSetup {
      // prepare
      val actor = createActor()

      // execute
      actor ? create
      (1 to 9).foreach(_ => actor ? createActivation)

      // verify
      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }
      snapshotSequenceNr(10) mustBe Some(10)
    }

    "deletes old snapshots when creating new ones" in new ToggleActorSetup {
      // prepare
      val actor = createActor()

      // execute
      actor ? create
      (1 to 9).foreach(_ => actor ? createActivation)
      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }
      (1 to 10).foreach(_ => actor ? createActivation)

      // verify
      waitFor(snapshotTimeout) { snapshotSequenceNr(20).isDefined }
      waitFor(snapshotTimeout) { snapshotSequenceNr(10).isEmpty }
      snapshotSequenceNr(10) mustBe None
      snapshotSequenceNr(20) mustBe Some(20)
    }

    "recover from snapshot" in new ToggleActorSetup {
      // prepare
      val actor = createActor()
      actor ? create
      (1 to 9).foreach(_ => actor ? createActivation)
      waitFor(30.seconds) { snapshotSequenceNr().isDefined }

      // execute
      val newActor = createActor()

      // verify
      val newToggle = fetchToggle(newActor)
      newToggle.name mustBe toggle.name
      primaryRollout(newToggle) mustBe createActivation.command.percentage
    }

    "reject create toggle after recovering existing toggle from snapshot" in new ToggleActorSetup {
      // prepare
      val actor = createActor()
      actor ? create
      (1 to 9).foreach(_ => actor ? createActivation)
      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }
      val newActor = createActor()

      // execute
      val response = await(newActor ? create)

      // verify
      response mustBe ToggleAlreadyExists(toggleId)
    }

    "recover deleted toggles from snapshot" in new ToggleActorSetup {
      // prepare
      val actor = createActor()
      actor ? create
      (1 to 8).foreach(_ => actor ? createActivation)
      actor ? delete
      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }

      // execute
      val newActor = createActor()

      // verify
      await(newActor ? GetToggle) mustBe None
    }

    "allow create toggle after recovering existing toggle from snapshot" in new ToggleActorSetup {
      // prepare
      val actor = createActor()
      actor ? create
      (1 to 8).foreach(_ => actor ? createActivation)
      actor ? delete
      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }
      val newActor = createActor()

      // execute
      val response = await(newActor ? create)

      // verify
      response mustBe CreateSucceeded(toggleId)
    }

    "recover inactive toggles from snapshot" in new ToggleActorSetup {
      // prepare
      val actor = createActor()
      actor ? create
      (1 to 8).foreach(_ => actor ? createActivation)
      actor ? deleteActivation
      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }

      // execute
      val newActor = createActor()

      // verify
      fetchToggle(newActor).activations mustBe empty
    }
  }
}
