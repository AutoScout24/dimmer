package toguru

import akka.actor.{ActorContext, ActorRef}
import toguru.toggles.events.StringSeq
import toguru.toggles.snapshots.ToggleActivationSnapshot

package object toggles {

  type ActorInitializer = (ActorContext, ActorRef) => Unit

  def fromProtoBuf(a: Map[String, Seq[String]]): Map[String, StringSeq] = a.map { case (k, v) => k -> StringSeq(v)}

  def toProtoBuf(a: Map[String, StringSeq]): Map[String, Seq[String]] = a.map { case (k, v) => k -> v.values }

  def fromProtoBuf(activations: Seq[ToggleActivationSnapshot]): IndexedSeq[ToggleActivation] =
    activations.map(a => ToggleActivation(a.rolloutPercentage, toProtoBuf(a.attributes))).to[Vector]

  def toProtoBuf(activations: IndexedSeq[ToggleActivation]): Seq[ToggleActivationSnapshot] =
    activations.map(a => ToggleActivationSnapshot(a.rolloutPercentage, fromProtoBuf(a.attributes)))
}
