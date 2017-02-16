package toguru.toggles

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads._
import toguru.toggles.ToggleActor._
import toguru.toggles.events.Rollout

object ToggleControllerJsonCommands {

  case class ActivationBody(rollout: Option[Rollout], attributes: Map[String, Seq[String]] = Map.empty) {
    def toCreate = CreateActivationCommand(rollout, attributes)
    def toUpdate(index: Int) = UpdateActivationCommand(index, rollout, attributes)
  }

  implicit val rolloutFormat: Format[Rollout] =
    (JsPath \ "percentage").format[Int](min(1) keepAnd max(100))
      .inmap(Rollout.apply, unlift(Rollout.unapply))

  implicit val activationFormat = Json.format[ToggleActivation]

  implicit val toggleFormat = Json.format[Toggle]

  implicit val createToggleFormat = Json.format[CreateToggleCommand]
  implicit val updateToggleFormat = Json.format[UpdateToggleCommand]

  implicit val activationBodyReads: Reads[ActivationBody] = (
    (JsPath \ "rollout").readNullable[Rollout] and
    (JsPath \ "attributes").readNullable[Map[String, Seq[String]]]
  )((r, maybeAtts) => ActivationBody(r, maybeAtts.getOrElse(Map.empty)))

  val activationBodyWrites: Writes[ActivationBody] = (
    (JsPath \ "rollout").writeNullable[Rollout] and
    (JsPath \ "attributes").write[Map[String, Seq[String]]]
  ) (unlift(ActivationBody.unapply))

  val sampleCreateToggle = CreateToggleCommand("toggle name", "toggle description", Map("team" -> "Toguru team"))
  val sampleUpdateToggle = UpdateToggleCommand(None, Some("new toggle description"), Some(Map("team" -> "Toguru team")))
  val sampleActivation = ActivationBody(Some(Rollout(42)), Map("country" -> Seq("de-DE", "de-AT", "DE")))

  val activationBodyParser = JsonResponses.json(sampleActivation)(activationBodyReads, activationBodyWrites)

}
