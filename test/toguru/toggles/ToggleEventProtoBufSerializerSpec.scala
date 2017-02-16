package toguru.toggles

import org.scalatest.{MustMatchers, WordSpec}
import toguru.toggles.events._

class ToggleEventProtoBufSerializerSpec extends WordSpec with MustMatchers {

  trait Setup {
    def rollout(p: Int) = Some(Rollout(p))

    val serializer = new ToggleEventProtoBufSerializer
  }

  "fromBinary" should {
    "transform rollout created to activation created" in new Setup {
      // prepare
      val bytes = GlobalRolloutCreated(30).toByteArray

      // execute
      val result = serializer.fromBinary(bytes, serializer.GlobalRolloutCreateManifest)

      // verify
      result mustBe ActivationCreated(rollout = rollout(30))
    }

    "transform rollout updated to activation updated" in new Setup {
      // prepare
      val bytes = GlobalRolloutUpdated(20).toByteArray

      // execute
      val result = serializer.fromBinary(bytes, serializer.GlobalRolloutUpdatedManifest)

      // verify
      result mustBe ActivationUpdated(rollout = rollout(20))
    }

    "transform rollout deleted to activation deleted" in new Setup {
      // prepare
      val bytes = GlobalRolloutDeleted().toByteArray

      // execute
      val result = serializer.fromBinary(bytes, serializer.GlobalRolloutDeletedManifest)

      // verify
      result mustBe ActivationDeleted()
    }
  }
}
