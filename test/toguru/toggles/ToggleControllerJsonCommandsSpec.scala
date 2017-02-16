package toguru.toggles

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsError, Json}
import toguru.toggles.ToggleControllerJsonCommands._


class ToggleControllerJsonCommandsSpec extends PlaySpec {

  val sampleActivationJson =
    Json.obj(
      "rollout" -> Json.obj("percentage" -> 42),
      "attributes" -> Json.obj("country" -> Json.arr("de-DE", "de-AT", "DE")))

  val singleValueActivation = Json.obj("attributes" -> Json.obj("country" -> "de-DE"))

  "Activations Json conversion" should {

    "parse attribute with single value" in {
      val parsedActivation = singleValueActivation.as[ActivationBody]

      parsedActivation.attributes mustBe Map("country" -> Seq("de-DE"))
    }

    "reject rollout percentages that are out of range" in {
      val invalidPercentage = Json.obj("rollout" -> Json.obj("percentage" -> 101))

      invalidPercentage.validate[ActivationBody] mustBe a[JsError]
    }

    "parse attributes with sequence value" in {
      val parsedActivation = sampleActivationJson.as[ActivationBody]

      parsedActivation mustBe sampleActivation
    }

    "parse empty activation" in {
      val parsedActivation = Json.obj().as[ActivationBody]

      parsedActivation.attributes mustBe empty
      parsedActivation.rollout mustBe empty
    }

    "correctly serialize sample activation to Json" in {
      val producedJson = activationBodyWrites.writes(sampleActivation)

      producedJson mustBe sampleActivationJson
    }
  }
}
