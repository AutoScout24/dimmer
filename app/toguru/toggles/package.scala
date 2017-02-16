package toguru

import akka.actor.{ActorContext, ActorRef}
import toguru.toggles.events.StringSeq
import toguru.toggles.snapshots.ToggleActivationSnapshot

package object toggles {

  type ActorInitializer = (ActorContext, ActorRef) => Unit

  def toProtoBuf(a: Map[String, Seq[String]]): Map[String, StringSeq] = a.map { case (k, v) => k -> StringSeq(v)}

  def toProtoBuf(activations: IndexedSeq[ToggleActivation]): Seq[ToggleActivationSnapshot] =
    activations.map(a => ToggleActivationSnapshot(toProtoBuf(a.attributes), a.rollout))

  def fromProtoBuf(a: Map[String, StringSeq]): Map[String, Seq[String]] = a.map { case (k, v) => k -> v.values }

  def fromProtoBuf(activations: Seq[ToggleActivationSnapshot]): IndexedSeq[ToggleActivation] =
    activations.map(a => ToggleActivation(fromProtoBuf(a.attributes), a.rollout)).to[Vector]
}
