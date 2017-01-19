package toguru.toggles

import play.api.Logger
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._

import scala.concurrent.Future
import scala.util.control.NonFatal

trait JsonResponses extends ResultPublishing {

  def errorJson(status: String, reason: String): JsObject =
    Json.obj("status" -> status, "reason" -> reason)

  def errorJson(status: String, reason: String, remedy: String): JsObject =
    Json.obj("status" -> status, "reason" -> reason, "remedy" -> remedy)

  def json[A](sample: A)(implicit reader: Reads[A], writer: Writes[A]): BodyParser[A] =
    BodyParser("json reader") { request =>
      import play.api.libs.iteratee.Execution.Implicits.trampoline
      BodyParsers.parse.tolerantJson(request).mapFuture {
        case Left(simpleResult) =>
          Future.successful(Left(badJsonResponse(Json.toJson(sample))))
        case Right(jsValue) =>
          jsValue.validate(reader).map { a =>
            Future.successful(Right(a))
          }.recoverTotal { jsError =>
            Future.successful(Left(badJsonResponse(Json.toJson(sample), Some(jsError))))
          }
      }
    }

  def badJsonResponse[A](sample: JsValue, jsError: Option[JsError] = None): Result = {
    val errorsObject = JsObject(jsError.to[List].flatMap(_.errors.map {
      case (path, errors) => path.toString() -> JsArray(errors.map(e => JsString(e.message)))
    }))

    BadRequest(
      errorJson(
        "Bad Request",
        "Provided json body is not valid",
        "Provide a valid json body of the form given in the sample field"
      ) ++
      Json.obj("sample" -> sample, "errors" -> errorsObject)
    )
  }

  def badHeaderResponse(request: Request[_], allowedHeaders: String*) = {
    import HeaderNames.ACCEPT

    val givenAcceptHeaders = request.acceptedTypes.mkString(", ")
    BadRequest(s"""
     |Bad Request
     |  No supported $ACCEPT request header provided (given was '$givenAcceptHeaders').
     |  Include ${allowedHeaders.mkString(", or")} in the $ACCEPT request header""".stripMargin)
  }

  def serverError(actionId: String, fields: (String, Any)*): PartialFunction[Throwable, Result] = {
    case NonFatal(ex) =>
      publishFailure(actionId, ex, fields: _*)
      InternalServerError(errorJson(
        "Internal server error",
        "An internal error occurred",
        "Try again later or contact service owning team"
      ))
  }

  object OnlyJson extends ActionFilter[Request] with HeaderNames with AcceptExtractors {

    override protected def filter[A](request: Request[A]): Future[Option[Result]] = Future.successful {
      request match {
        case Accepts.Json() => None
        case _ => Some(badHeaderResponse(request, MimeTypes.JSON))
      }
    }
  }


  val ActionWithJson = Action andThen OnlyJson
}
