package toguru.toggles

import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import play.mvc.Http.HeaderNames.AUTHORIZATION
import com.github.t3hnar.bcrypt._

import scala.concurrent.Future
import scala.language.higherKinds

trait Authentication {
  import Authentication.{ Config, Refiner }

  def Authenticate[R[T] <: Request[T]](config: Config): Refiner[R] = Authentication.authenticateRefiner(config)
}

object Authentication {
  val ApiKeyPrefix = "api-key"
  val ApiKeyRegex = s"\\s*$ApiKeyPrefix\\s+([^\\s]+)\\s*".r

  sealed trait Principal {
    def name: String
  }

  case class ApiKeyPrincipal(name: String) extends Principal

  case object DevUser extends Principal { val name = "dev" }

  case class ApiKey(name: String, hash: String)

  case class Config(apiKeys: Seq[ApiKey], disabled: Boolean)

  class AuthenticatedRequest[A](val principal: Principal, request: Request[A]) extends WrappedRequest(request)

  type Refiner[R[_]] = ActionRefiner[R, AuthenticatedRequest]

  def authenticateRefiner[R[T] <: Request[T]](config: Config): Refiner[R] = new Refiner[R] {

    override protected def refine[A](request: R[A]): Future[Either[Result, AuthenticatedRequest[A]]] =
      extractPrincipal(config, request) match {
        case Some(p) => Future.successful(Right(new AuthenticatedRequest(p, request)))
        case None    => Future.successful(Left(UnauthorizedResponse))
      }
  }

  def extractPrincipal[A](config: Config, request: Request[A]): Option[Principal] = {
    def toPrincipal: String => Option[Principal] = {

      case ApiKeyRegex(key) =>
        config.apiKeys.collectFirst { case ApiKey(name, hash) if key.isBcrypted(hash) => ApiKeyPrincipal(name) }

      case _  =>
        None
    }

    val maybeHeader = request.headers.get(AUTHORIZATION)
    if (config.disabled)
      // if header exist, try to extract the principal. If the header is missing, yield the dev user.
      maybeHeader.map(toPrincipal).getOrElse(Some(DevUser))
    else
      maybeHeader.flatMap(toPrincipal)
  }

  val UnauthorizedResponse: Result = Unauthorized(Json.obj(
      "status"  -> "Unauthorized",
      "message" -> s"$AUTHORIZATION header missing or invalid",
      "remedy"  -> s"Provide a valid $AUTHORIZATION header '$AUTHORIZATION: $ApiKeyPrefix [your-api-key]' in your request"
    ))
}