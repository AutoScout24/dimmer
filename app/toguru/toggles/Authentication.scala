package toguru.toggles

import play.api.mvc.{RequestHeader, Result}
import play.api.mvc.Security.AuthenticatedBuilder
import play.mvc.Http.HeaderNames
import Authentication._
import play.api.libs.json.Json
import play.api.mvc.Results._

object Authentication {
  val ApiKeyPrefix = "api-key"
  val ApiKeyRegex = s"\\s*$ApiKeyPrefix\\s+([^\\s]+)\\s*".r

  sealed trait Principal {
    def name: String
  }

  case class ApiKeyPrincipal(name: String) extends Principal

  case object DevUser extends Principal { val name = "dev" }

  case class ApiKey(name: String, key: String)

  case class Config(apiKeys: Seq[ApiKey], disabled: Boolean)
}


trait Authentication {

  def authConfig: Authentication.Config

  object Authenticated extends AuthenticatedBuilder[Principal](extractPrincipal, unauthorizedResponse)

  def extractPrincipal[A](request: RequestHeader): Option[Principal] = {
    def toPrincipal: String => Option[Principal] = {

      case ApiKeyRegex(key) =>
        authConfig.apiKeys.collectFirst { case ApiKey(name, `key`) => ApiKeyPrincipal(name) }

      case _  =>
        None
    }

    if(authConfig.disabled)
      Some(DevUser)
    else
      request.headers.get(HeaderNames.AUTHORIZATION).flatMap(toPrincipal)
  }

  def unauthorizedResponse(header: RequestHeader): Result = Unauthorized(Json.obj(
      "status"  -> "Unauthorized",
      "message" -> "Authentication header missing or invalid",
      "remedy"  -> s"Provide a valid Authentication header 'Authentication: $ApiKeyPrefix [your-api-key]' in your request"
    ))
}
