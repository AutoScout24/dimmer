package toguru.toggles

case class Toggle(
             id: String,
             name: String,
             description: String,
             tags: Map[String, String] = Map.empty,
             activations: IndexedSeq[ToggleActivation] = IndexedSeq.empty,
             rolloutPercentage: Option[Int] = None)

case class ToggleActivation(
                           rolloutPercentage: Option[Int] = None,
                           attributes: Map[String,Seq[String]] = Map.empty)