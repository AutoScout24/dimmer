package toguru.toggles

import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import play.mvc.Http.HeaderNames
import toguru.toggles.Authentication._

import scala.concurrent.Future

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

  class AuthenticatedRequest[A](val principal: Principal, request: Request[A]) extends WrappedRequest(request)
}

trait Authentication {

  import scala.language.higherKinds

  type Refiner[R[_]] = ActionRefiner[R, AuthenticatedRequest]

  object Authenticate {

    def apply[R[_] <: Request[_]](config: Config): Refiner[R] = new Refiner[R] {

      override protected def refine[A](r: R[A]): Future[Either[Result, AuthenticatedRequest[A]]] = {
        // type inference can't establish R[A] <: Request[A] here - this cast might be actually unsound.
        val request = r.asInstanceOf[Request[A]]
        extractPrincipal(config, request) match {
          case Some(p) => Future.successful(Right(new AuthenticatedRequest(p, request)))
          case None    => Future.successful(Left(unauthorizedResponse(request)))
        }
      }
    }
  }

  def extractPrincipal[A](config: Config, request: RequestHeader): Option[Principal] = {
    def toPrincipal: String => Option[Principal] = {

      case ApiKeyRegex(key) =>
        config.apiKeys.collectFirst { case ApiKey(name, `key`) => ApiKeyPrincipal(name) }

      case _  =>
        None
    }

    if(config.disabled)
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
