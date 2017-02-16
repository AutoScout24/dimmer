package toguru.toggles

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Reads._
import toguru.toggles.ToggleActor._
import toguru.toggles.events.Rollout

object ToggleControllerJsonCommands {

  case class ActivationBody(attributes: Map[String, Seq[String]] = Map.empty, rollout: Option[Rollout]) {
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

  val mapOfSeqReads: Reads[Map[String, Seq[String]]] = {
    val valuesReads = Reads.seq[String] or StringReads.map(Seq(_))
    Reads.map(valuesReads)
  }

  implicit val activationBodyReads: Reads[ActivationBody] = (
    (JsPath \ "rollout").readNullable[Rollout] and
    (JsPath \ "attributes").readNullable(mapOfSeqReads)
  )((r, maybeAtts) => ActivationBody(maybeAtts.getOrElse(Map.empty), r))

  val activationBodyWrites: Writes[ActivationBody] = (
    (JsPath \ "attributes").write[Map[String, Seq[String]]] and
    (JsPath \ "rollout").writeNullable[Rollout]
  ) (unlift(ActivationBody.unapply))

  val sampleCreateToggle = CreateToggleCommand("toggle name", "toggle description", Map("team" -> "Toguru team"))
  val sampleUpdateToggle = UpdateToggleCommand(None, Some("new toggle description"), Some(Map("team" -> "Toguru team")))
  val sampleActivation = ActivationBody(Map("country" -> Seq("de-DE", "de-AT", "DE")), Some(Rollout(42)))

  val activationBodyParser = JsonResponses.json(sampleActivation)(activationBodyReads, activationBodyWrites)

}
