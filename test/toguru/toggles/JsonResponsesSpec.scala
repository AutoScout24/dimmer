package toguru.toggles

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{Format, Json}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, FutureAwaits}
import toguru.toggles.JsonResponsesSpec.TestData

object JsonResponsesSpec {
  case class TestData(s: String, i: Int, m: Map[String, String])
}

class JsonResponsesSpec extends PlaySpec with FutureAwaits with DefaultAwaitTimeout {

  implicit override def defaultAwaitTimeout = 1000.millis

  val config = ConfigFactory.parseResources("test-actors.conf").resolve()

  implicit val system = ActorSystem("test", config)

  implicit val mat = ActorMaterializer()

  trait JsonResponsesSetup extends JsonResponses {

    val sample = TestData("name", 3, Map("one" -> "1"))

    implicit val format = Json.format[TestData]

    def parse[A](sample: A, format: Format[A], body: String)(implicit mat: Materializer) = {
      await(
        json(sample)(format, format)(FakeRequest()).run(Source.single(ByteString(body.getBytes("utf-8"))))
      )
    }
  }

  "json body parser" should {
    "parse valid json request body" in new JsonResponsesSetup {
      val parsed = parse(sample, format, Json.toJson(sample).toString)
      parsed mustBe Right(sample)
    }

    "reject invalid json request body" in new JsonResponsesSetup {
      val parsed = parse(sample, format, """{ "wrong" : true }""")
      parsed mustBe a[Left[_,_]]
    }
  }
}

