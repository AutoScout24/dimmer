package toguru.toggles

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.persistence.query.EventEnvelope
import toguru.toggles.AuditLog.Entry
import toguru.toggles.AuditLogActor._
import toguru.toggles.events._

import scala.concurrent.duration._

class AuditLogActorSpec extends ActorSpec {

  def meta(time: Long) = Some(Metadata(time, "testUser"))

  def rollout(p: Int) = Some(Rollout(p))

  val events = List(
    Entry("toggle-1", ToggleCreated("toggle 1", "first toggle", Map("team" -> "Toguru team"), meta(0))),
    Entry("toggle-1", ToggleUpdated("toggle 1", "very first toggle", Map("team" -> "Toguru team"), meta(10))),
    Entry("toggle-1", ActivationCreated(meta(20), 0, Map("country" -> StringSeq(Seq("de-DE", "de-AT"))), None)),
    Entry("toggle-1", ActivationUpdated(meta(30), 0, Map("country" -> StringSeq(Seq("de-DE", "de-AT"))), rollout(34))),
    Entry("toggle-1", ActivationDeleted(meta(40), 0)),
    Entry("toggle-1", ActivationCreated(meta(50), 0, rollout = rollout(10))),
    Entry("toggle-1", ActivationUpdated(meta(60), 0, rollout = rollout(100))),
    Entry("toggle-1", ActivationDeleted(meta(70), 0))
  )

  def createActor(events: Seq[Entry] = List.empty, time: Long = 0, retentionLength: Int = 10, retentionTime: FiniteDuration = 10.seconds): ActorRef =
    system.actorOf(Props(new AuditLogActor((_,_) => (), () => time, retentionTime, retentionLength, events)))

  def sendEvents(actor: ActorRef) =
    events.foreach(e => actor ! EventEnvelope(0, e.id, 0, e.event))

  "audit log actor" should {
    "return current audit log" in {
      val actor = createActor(events)

      val response = await(actor ? GetLog)

      response mustBe events
    }

    "build audit log from events" in {
      val actor = createActor()

      val id1 = "toggle-1"
      val id2 = "toggle-2"

      sendEvents(actor)

      val response = await(actor ? GetLog)

      response mustBe events.reverse
    }

    "truncate audit log on insertion" in {
      val actor = createActor(retentionLength = 3)

      sendEvents(actor)

      val response = await(actor ? GetLog)

      response mustBe events.reverse.take(3)
    }

    "truncate audit log on cleanup" in {
      val actor = createActor(events = events.reverse, retentionTime = 100.millis, time = 125)

      actor ! Cleanup

      val response = await(actor ? GetLog)

      response.asInstanceOf[Seq[_]].length mustBe 5

      response mustBe events.reverse.take(5)
    }
  }
}
