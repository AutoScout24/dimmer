package toguru.toggles

import toguru.toggles.events.Rollout

case class Toggle(
             id: String,
             name: String,
             description: String,
             tags: Map[String, String] = Map.empty,
             activations: IndexedSeq[ToggleActivation] = IndexedSeq.empty,
             rolloutPercentage: Option[Int] = None)

case class ToggleActivation(attributes: Map[String, Seq[String]] = Map.empty, rollout: Option[Rollout] = None)
