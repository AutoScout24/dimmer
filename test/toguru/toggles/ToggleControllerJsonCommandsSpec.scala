package toguru.toggles

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsError, Json}
import toguru.toggles.ToggleControllerJsonCommands._
import toguru.toggles.events.Rollout


class ToggleControllerJsonCommandsSpec extends PlaySpec {

  val sampleActivationJson =
    Json.obj(
      "rollout" -> Json.obj("percentage" -> 42),
      "attributes" -> Json.obj("country" -> Json.arr("de-DE", "de-AT", "DE")))

  val singleValueActivation = Json.obj("attributes" -> Json.obj("country" -> "de-DE"))

  "Activations Json conversion" should {

    "parse attribute with single value" in {
      // execute
      val parsedActivation = singleValueActivation.as[ActivationBody]

      // verify
      parsedActivation.attributes mustBe Map("country" -> Seq("de-DE"))
    }

    "reject rollout percentages that are out of range" in {
      // prepare
      val invalidPercentage = Json.obj("rollout" -> Json.obj("percentage" -> 101))

      // execute & verify
      invalidPercentage.validate[ActivationBody] mustBe a[JsError]
    }

    "accept rollout percentage of zero" in {
      // prepare
      val zeroPercentage = Json.obj("rollout" -> Json.obj("percentage" -> 0))

      // execute & verify
      zeroPercentage.as[ActivationBody] mustBe ActivationBody(Map.empty, Some(Rollout(0)))
    }

    "parse attributes with sequence value" in {
      // execute
      val parsedActivation = sampleActivationJson.as[ActivationBody]

      // verify
      parsedActivation mustBe sampleActivation
    }

    "parse empty activation" in {
      // execute
      val parsedActivation = Json.obj().as[ActivationBody]

      // verify
      parsedActivation.attributes mustBe empty
      parsedActivation.rollout mustBe empty
    }

    "correctly serialize sample activation to Json" in {
      // execute
      val producedJson = activationBodyWrites.writes(sampleActivation)

      // verify
      producedJson mustBe sampleActivationJson
    }
  }
}
