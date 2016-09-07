package toguru.toggles

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import akka.serialization.SerializerWithStringManifest
import com.trueaccord.scalapb.GeneratedMessage

/**
  * Marker trait for toggle events.
  */
trait ToggleEvent extends GeneratedMessage {
  def meta: Option[Metadata]
}

/**
  * Serializer for toggle events.
  */
class ToggleEventProtoBufSerializer extends SerializerWithStringManifest {

  override def identifier: Int = 4001

  final val CreatedManifest              = classOf[ToggleCreated].getSimpleName
  final val GlobalRolloutCreateManifest  = classOf[GlobalRolloutCreated].getSimpleName
  final val GlobalRolloutUpdatedManifest = classOf[GlobalRolloutUpdated].getSimpleName
  final val GlobalRolloutDeletedManifest = classOf[GlobalRolloutDeleted].getSimpleName

  override def manifest(o: AnyRef): String = o.getClass.getSimpleName

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case CreatedManifest              => ToggleCreated.parseFrom(bytes)
    case GlobalRolloutCreateManifest  => GlobalRolloutCreated.parseFrom(bytes)
    case GlobalRolloutUpdatedManifest => GlobalRolloutUpdated.parseFrom(bytes)
    case GlobalRolloutDeletedManifest => GlobalRolloutDeleted.parseFrom(bytes)
  }

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case e: ToggleEvent => e.toByteArray
  }
}

/**
  * Tagging write adapter for toggles.
  * Required for persistent queries by tag.
  */
class ToggleEventTagging extends WriteEventAdapter {
  override def manifest(event: Any): String = ""

  def withTag(event: Any, tags: String*) = Tagged(event, tags.to[Set])

  override def toJournal(event: Any): Any = event match {
    case _: ToggleEvent          => withTag(event, "toggle")
    case _                       => event
  }
}