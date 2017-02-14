package toguru

import akka.actor.{ActorContext, ActorRef}
import toguru.toggles.events.CustomAttributeValue

package object toggles {

  type ActorInitializer = (ActorContext, ActorRef) => Unit

  def toEventFormat(a: Map[String, Seq[String]]): Map[String, CustomAttributeValue] = a.map { case (k, v) => k -> CustomAttributeValue(v)}

  def fromEventFormat(a: Map[String, CustomAttributeValue]): Map[String, Seq[String]] = a.map { case (k, v) => k -> v.values }

}
