package toguru.toggles

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.persistence.inmemory.extension.InMemorySnapshotStorage.SnapshotForMaxSequenceNr
import akka.persistence.inmemory.extension.StorageExtension
import akka.persistence.inmemory.snapshotEntry
import akka.persistence.query.PersistenceQuery
import akka.persistence.query.scaladsl._
import akka.stream.scaladsl._
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
    val createActivationCmd = CreateActivationCommand(Some(42))
    val create = authenticated(createCmd)
    val update = authenticated(updateCmd)
    val delete = authenticated(DeleteToggleCommand)
    val createActivation = authenticated(createActivationCmd)
    val updateActivationCmd = UpdateActivationCommand(0, Some(55), Map("a" -> Seq("b")))
    val updateActivation = authenticated(updateActivationCmd)
    val deleteActivation = authenticated(DeleteActivationCommand(0))

    def createActor(toggle: Option[Toggle] = None) = system.actorOf(Props(new ToggleActor(toggleId, toggle)))

    def fetchToggle(actor: ActorRef): Toggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get

    def snapshotSequenceNr(maxSequenceNr: Long = 100): Option[Long] =
      await(StorageExtension(system).snapshotStorage ? SnapshotForMaxSequenceNr(toggleId, maxSequenceNr)).asInstanceOf[Option[snapshotEntry]].map(_.sequenceNumber)
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

    "keeps toggle description and tags when updating names" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))

      override val updateCmd = UpdateToggleCommand(Some("new name"), None, None)

      val response = await(actor ? authenticated(updateCmd))
      response mustBe Success

      fetchToggle(actor) mustBe Toggle(toggleId, updateCmd.name.get, toggle.description, toggle.tags)
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

    "allow to re-create toggle after delete" in new ToggleActorSetup {
      val actor = createActor(Some(toggle.copy(name = "initial toggle name")))
      actor ? delete

      val response = await(actor ? create)

      response mustBe CreateSucceeded(toggleId)
      fetchToggle(actor) mustBe toggle
    }

    "create activation condition when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? createActivation)
      response mustBe CreateActivationSuccess(0)

      val actorToggle = await(actor ? GetToggle).asInstanceOf[Some[Toggle]].get
      actorToggle.activations(0).rolloutPercentage mustBe createActivationCmd.percentage
    }

    "update activation condition when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle.copy(rolloutPercentage = Some(55))))
      val response = await(actor ? updateActivation)
      response mustBe Success

      val fToggle = fetchToggle(actor)
      fToggle.activations must have length (1)
      fToggle.activations(0).rolloutPercentage mustBe updateActivation.command.percentage
    }

    "reject set global rollout condition command when toggle does not exists" in new ToggleActorSetup {
      val actor = createActor()
      val response = await(actor ? createActivation)
      response mustBe ToggleDoesNotExist(toggleId)
    }

    "delete activation when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle.copy(rolloutPercentage = Some(42))))
      val response = await(actor ? deleteActivation)
      response mustBe Success

      fetchToggle(actor).activations mustBe empty
    }

    "return success on delete when rollout condition does not exist" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val response = await(actor ? deleteActivation)
      response mustBe Success
    }

    "create activations when receiving command" in new ToggleActorSetup {
      val actor = createActor(Some(toggle))
      val cmd = authenticated(CreateActivationCommand(Some(53), Map("culture" -> Seq("de-DE", "de-AT"))))
      val response = await(actor ? cmd)
      response mustBe CreateActivationSuccess(0)
      val activation = fetchToggle(actor).activations(0)
      activation mustBe ToggleActivation(Some(53), Map("culture" -> Seq("de-DE", "de-AT")))
    }

    "persist toggle events" in new ToggleActorSetup {
      val actor = system.actorOf(Props(new ToggleActor(toggleId, None) {
        override def time() = 0
      }))

      lazy val readJournal = PersistenceQuery(system).readJournalFor("inmemory-read-journal")
        .asInstanceOf[ReadJournal with CurrentEventsByPersistenceIdQuery]

      actor ? create
      actor ? createActivation
      actor ? updateActivation
      await(actor ? deleteActivation)

      val eventualEnvelopes = readJournal.currentEventsByPersistenceId(toggleId, 0, 100).runWith(Sink.seq)
      val events = await(eventualEnvelopes).map(_.event)
      val meta = Some(Metadata(0, testUser))
      val expectedEvents = Seq(
        ToggleCreated(toggle.name, toggle.description, toggle.tags, meta),
        ActivationCreated(0, createActivationCmd.percentage),
        ActivationUpdated(0, updateActivationCmd.percentage, fromProtoBuf(updateActivationCmd.attributes), meta),
        ActivationDeleted(0, meta)
      )

      events(0) mustBe ToggleCreated(toggle.name, toggle.description, toggle.tags, meta)
      events(1) mustBe ActivationCreated(0, createActivationCmd.percentage, Map.empty, meta)
      events(2) mustBe ActivationUpdated(0, updateActivationCmd.percentage, fromProtoBuf(updateActivationCmd.attributes), meta)
      events(3) mustBe ActivationDeleted(0, meta)
    }

    "save snapshots every 10 events" in new ToggleActorSetup {
      val actor = createActor()

      actor ? create
      (1 to 9).foreach(_ => actor ? createActivation)

      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }

      snapshotSequenceNr(10) mustBe Some(10)
    }

    "deletes old snapshots when creating new ones" in new ToggleActorSetup {
      val actor = createActor()

      actor ? create
      (1 to 9).foreach(_ => actor ? createActivation)

      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }

      (1 to 10).foreach(_ => actor ? createActivation)


      waitFor(snapshotTimeout) { snapshotSequenceNr(20).isDefined }
      waitFor(snapshotTimeout) { snapshotSequenceNr(10).isEmpty }

      snapshotSequenceNr(10) mustBe None
      snapshotSequenceNr(20) mustBe Some(20)
    }

    "recover from snapshot" in new ToggleActorSetup {
      val actor = createActor()
      actor ? create
      (1 to 9).foreach(_ => actor ? createActivation)

      waitFor(30.seconds) { snapshotSequenceNr().isDefined }

      val newActor = createActor()

      val newToggle = fetchToggle(newActor)

      newToggle.name mustBe toggle.name
      newToggle.activations(0).rolloutPercentage mustBe createActivation.command.percentage
    }

    "reject create toggle after recovering existing toggle from snapshot" in new ToggleActorSetup {
      val actor = createActor()
      actor ? create
      (1 to 9).foreach(_ => actor ? createActivation)

      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }

      val newActor = createActor()
      val response = await(newActor ? create)
      response mustBe ToggleAlreadyExists(toggleId)
    }

    "recover deleted toggles from snapshot" in new ToggleActorSetup {
      val actor = createActor()
      actor ? create
      (1 to 8).foreach(_ => actor ? createActivation)
      actor ? delete

      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }

      val newActor = createActor()

      await(newActor ? GetToggle) mustBe None
    }


    "allow create toggle after recovering existing toggle from snapshot" in new ToggleActorSetup {
      val actor = createActor()
      actor ? create
      (1 to 8).foreach(_ => actor ? createActivation)
      actor ? delete

      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }

      val newActor = createActor()
      val response = await(newActor ? create)
      response mustBe CreateSucceeded(toggleId)
    }

    "recover inactive toggles from snapshot" in new ToggleActorSetup {
      val actor = createActor()
      actor ? create
      (1 to 8).foreach(_ => actor ? createActivation)
      actor ? deleteActivation

      waitFor(snapshotTimeout) { snapshotSequenceNr().isDefined }

      val newActor = createActor()

      fetchToggle(newActor).activations mustBe empty
    }
  }
}
