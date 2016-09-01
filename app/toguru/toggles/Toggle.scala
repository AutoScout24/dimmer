package toguru.toggles

case class Toggle(
             id: String,
             name: String,
             description: String,
             tags: Map[String, String] = Map.empty,
             rolloutPercentage: Option[Int] = None)
