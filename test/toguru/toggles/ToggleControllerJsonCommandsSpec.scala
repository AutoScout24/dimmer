package toguru.toggles

import org.scalatestplus.play.PlaySpec
import play.api.libs.json
import play.api.libs.json.{JsValue, Json}
import toguru.toggles.ToggleActor.UpdateActivationCommand
import toguru.toggles.ToggleControllerJsonCommands._


class ToggleControllerJsonCommandsSpec extends PlaySpec {


  "Activations" should {
    val refJson =
      """{ "rollout" : {
        |    "percentage": 42
        |  },
        |  "attributes": {
        |    "country": [ "de-DE", "de-AT" ]
        |  }
        |}
      """.stripMargin.replaceAll("(\\s|\\n)+", "")

    "Json deserialize with sequence" in {
      val sampleJsonObj = Json.parse(refJson).as[ActivationBody]

      sampleJsonObj mustBe sampleActivation
    }

    "serialize to Json" in {
      val sampleJsonString = activationBodyWrites.writes(sampleActivation)

      sampleJsonString mustBe Json.parse(refJson)
    }
  }
}
