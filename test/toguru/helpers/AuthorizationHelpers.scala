package toguru.helpers

import com.github.t3hnar.bcrypt._
import play.api.http.HeaderNames
import play.api.test.FakeRequest
import toguru.toggles.Authentication
import toguru.toggles.Authentication.ApiKey

trait AuthorizationHelpers {

  val validApiKeyString = "valid-test-key"

  val validApiKey = ApiKey("valid-key", validApiKeyString.bcrypt)

  val authConfig = Authentication.Config(Seq(validApiKey), disabled = false)

  def requestWithApiKey(apiKey: String) =
    FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"${Authentication.ApiKeyPrefix} $apiKey")

  def authorizedRequest = requestWithApiKey(validApiKeyString)
}
