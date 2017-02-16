package toguru.toggles

import com.github.t3hnar.bcrypt._
import org.scalatestplus.play.PlaySpec
import play.api.http.HeaderNames
import play.api.test.FakeRequest
import toguru.toggles.Authentication._

class AuthenticationSpec extends PlaySpec {

  trait Setup extends Authentication {
    val testKeyString = "valid-key"
    val testKey   = ApiKey("test", testKeyString.bcrypt)
    val principal = ApiKeyPrincipal(testKey.name)

    val authenticatedRequest = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"$ApiKeyPrefix $testKeyString")
    val missingHeaderRequest = FakeRequest()
    val badKeyRequest        = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"$ApiKeyPrefix invalid-key")
    val badHeaderRequest     = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"some arbitrary text")

    val enabledConfig  = Config(Seq(testKey), disabled = false)
    val disabledConfig = Config(Seq(testKey), disabled = true)
  }

  "extractPrincipal" when {
    "authentication is enabled" should {
      "extract principal from authenticated request" in new Setup {
        extractPrincipal(enabledConfig, authenticatedRequest) mustBe Some(principal)
      }

      "reject request with wrong key" in new Setup {
        extractPrincipal(enabledConfig, badKeyRequest) mustBe None
      }

      "reject request with wrong header" in new Setup {
        extractPrincipal(enabledConfig, badHeaderRequest) mustBe None
      }

      "reject request without key" in new Setup {
        extractPrincipal(enabledConfig, missingHeaderRequest) mustBe None
      }
    }

    "authentication is disabled" should {
      "extract principal from authenticated request" in new Setup {
        extractPrincipal(disabledConfig, authenticatedRequest) mustBe Some(principal)
      }

      "reject request with wrong key" in new Setup {
        extractPrincipal(disabledConfig, badKeyRequest) mustBe None
      }

      "reject request with wrong header" in new Setup {
        extractPrincipal(disabledConfig, badHeaderRequest) mustBe None
      }

      "accept request without key" in new Setup {
        extractPrincipal(disabledConfig, missingHeaderRequest) mustBe Some(DevUser)
      }
    }
  }
}
