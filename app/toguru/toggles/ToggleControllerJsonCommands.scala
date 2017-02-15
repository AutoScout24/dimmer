package toguru.toggles

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads._
import toguru.toggles.ToggleActor.{apply => _, _}

object ToggleControllerJsonCommands {

  case class ActivationBody(percentage: Option[Int], attributes: Map[String, Seq[String]] = Map.empty) {
    def toCreate = CreateActivationCommand(percentage, attributes)
    def toUpdate(index: Int) = UpdateActivationCommand(index, percentage, attributes)
  }

  implicit val activationFormatReads = Json.format[ToggleActivation]

  implicit val toggleFormat = Json.format[Toggle]

  implicit val createToggleFormat = Json.format[CreateToggleCommand]
  implicit val updateToggleFormat = Json.format[UpdateToggleCommand]
  implicit val globalRolloutFormat: Format[SetGlobalRolloutCommand] =
    (JsPath \ "percentage").format[Int](min(1) keepAnd max(100)).inmap(
      SetGlobalRolloutCommand.apply, unlift(SetGlobalRolloutCommand.unapply))

  implicit val activationBodyReads: Reads[ActivationBody] = (
    (JsPath \ "rollout" \ "percentage").readNullable[Int](min(1) keepAnd max(100)) and
    (JsPath \ "attributes").readNullable[Map[String, Seq[String]]]
  )((r, maybeAtts) => ActivationBody(r, maybeAtts.getOrElse(Map.empty)))

  val activationBodyWrites: Writes[ActivationBody] = (
    (JsPath \ "rollout" \ "percentage").writeNullable[Int] and
    (JsPath \ "attributes").write[Map[String, Seq[String]]]
  ) (unlift(ActivationBody.unapply))

  val sampleCreateToggle = CreateToggleCommand("toggle name", "toggle description", Map("team" -> "Toguru team"))
  val sampleUpdateToggle = UpdateToggleCommand(None, Some("new toggle description"), Some(Map("team" -> "Toguru team")))
  val sampleSetGlobalRollout = SetGlobalRolloutCommand(42)
  val sampleActivation = ActivationBody(Some(42), Map("country" -> Seq("de-DE", "de-AT", "DE")))

  val activationBodyParser = JsonResponses.json(sampleActivation)(activationBodyReads, activationBodyWrites)

}
