package toguru.toggles

import org.scalatestplus.play.PlaySpec
import play.api.http.HeaderNames
import play.api.test.FakeRequest
import toguru.toggles.Authentication._

class AuthenticationSpec extends PlaySpec {

  trait AuthenticationSetup extends Authentication {
    val testKey   = ApiKey("test", "valid-key")
    val principal = ApiKeyPrincipal(testKey.name)

    val authenticatedRequest = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"$ApiKeyPrefix ${testKey.key}")
    val missingHeaderRequest = FakeRequest()
    val badKeyRequest        = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"$ApiKeyPrefix invalid-key")
    val badHeaderRequest     = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"some arbitrary text")

    val enabledConfig  = Config(Seq(testKey), disabled = false)
    val disabledConfig = Config(Seq(testKey), disabled = true)
  }

  "extractPrincipal" when {
    "authentication is enabled" should {
      "extract principal from authenticated request" in new AuthenticationSetup {
        extractPrincipal(enabledConfig, authenticatedRequest) mustBe Some(principal)
      }

      "reject request with wrong key" in new AuthenticationSetup {
        extractPrincipal(enabledConfig, badKeyRequest) mustBe None
      }

      "reject request with wrong header" in new AuthenticationSetup {
        extractPrincipal(enabledConfig, badHeaderRequest) mustBe None
      }

      "reject request without key" in new AuthenticationSetup {
        extractPrincipal(enabledConfig, missingHeaderRequest) mustBe None
      }
    }

    "authentication is disabled" should {
      "extract principal from authenticated request" in new AuthenticationSetup {
        extractPrincipal(disabledConfig, authenticatedRequest) mustBe Some(principal)
      }

      "reject request with wrong key" in new AuthenticationSetup {
        extractPrincipal(disabledConfig, badKeyRequest) mustBe None
      }

      "reject request with wrong header" in new AuthenticationSetup {
        extractPrincipal(disabledConfig, badHeaderRequest) mustBe None
      }

      "accept request without key" in new AuthenticationSetup {
        extractPrincipal(disabledConfig, missingHeaderRequest) mustBe Some(DevUser)
      }
    }
  }
}
