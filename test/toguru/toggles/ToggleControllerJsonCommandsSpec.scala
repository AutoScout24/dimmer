package toguru.toggles

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import toguru.toggles.ToggleControllerJsonCommands._


class ToggleControllerJsonCommandsSpec extends PlaySpec {

  val sampleActivationJson =
    Json.obj(
      "rollout" -> Json.obj("percentage" -> 42),
      "attributes" -> Json.obj("country" -> Json.arr("de-DE", "de-AT", "DE")))

  val singleValueActivation =
    Json.obj("attributes" -> Json.obj("country" -> "de-DE"))

  "Activations Json conversion" should {

    "parse attribute with single value" ignore {
      val parsedActivation = singleValueActivation.as[ActivationBody]

      parsedActivation.attributes mustBe Map("country" -> Seq("de-DE"))
    }

    "parse attributes with sequence value" in {
      val parsedActivation = sampleActivationJson.as[ActivationBody]

      parsedActivation mustBe sampleActivation
    }

    "correctly serialize sample activation to Json" in {
      val producedJson = activationBodyWrites.writes(sampleActivation)

      producedJson mustBe sampleActivationJson
    }
  }
}
