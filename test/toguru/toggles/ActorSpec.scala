package toguru.toggles

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers}
import org.scalatestplus.play.PlaySpec
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

import scala.concurrent.duration._

trait ActorSpec extends PlaySpec with MustMatchers with BeforeAndAfterAll with FutureAwaits with DefaultAwaitTimeout {

  implicit override def defaultAwaitTimeout = 500.millis

  val config = ConfigFactory.parseResources("test-actors.conf").resolve()

  implicit val system = ActorSystem("test", config)

  implicit val mat = ActorMaterializer()

}
