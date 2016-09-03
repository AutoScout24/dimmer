package toguru.toggles

import akka.actor.ActorSystem
import akka.persistence.inmemory.extension._
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, MustMatchers}
import org.scalatestplus.play.PlaySpec
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

import scala.concurrent.Await
import scala.concurrent.duration._

trait ActorSpec extends PlaySpec
  with MustMatchers
  with BeforeAndAfterAll
  with BeforeAndAfterEach
  with FutureAwaits
  with DefaultAwaitTimeout {

  implicit override def defaultAwaitTimeout = 1000.millis

  val config = ConfigFactory.parseResources("test-actors.conf").resolve()

  implicit val system = ActorSystem("test", config)

  implicit val mat = ActorMaterializer()

  override protected def beforeEach(): Unit = {
    val tp = TestProbe()
    tp.send(StorageExtension(system).journalStorage, InMemoryJournalStorage.ClearJournal)
    tp.expectMsg(akka.actor.Status.Success(""))
    tp.send(StorageExtension(system).snapshotStorage, InMemorySnapshotStorage.ClearSnapshots)
    tp.expectMsg(akka.actor.Status.Success(""))
    super.beforeEach()
  }

  override protected def afterAll() = {
    Await.ready(system.terminate(), Duration.Inf)
    super.afterAll()
  }
}
