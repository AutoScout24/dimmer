package toguru.helpers

import play.api.http.HeaderNames
import play.api.test.FakeRequest
import toguru.toggles.Authentication
import toguru.toggles.Authentication.ApiKey

trait AuthorizationHelpers {

  val validApiKey = ApiKey("valid-key", "valid-test-key")

  val authConfig = Authentication.Config(Seq(validApiKey), disabled = false)

  def requestWithApiKey(apiKey: String) =
    FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> s"${Authentication.ApiKeyPrefix} $apiKey")

  def authorizedRequest = requestWithApiKey(validApiKey.key)
}
