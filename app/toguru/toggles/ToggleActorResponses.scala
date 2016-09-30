package toguru.toggles

import play.api.mvc.{Result, Results}
import toguru.toggles.ToggleActor.{PersistFailed, ToggleDoesNotExist}

trait ToggleActorResponses extends Results with JsonResponses {

  type ResponseHandler  = Any => Result
  type HandlerDecorator = ResponseHandler => ResponseHandler

  def both(left: HandlerDecorator, right: HandlerDecorator): HandlerDecorator = left.andThen(right)

  def whenToggleExists(handler: ResponseHandler)(implicit actionId: String): ResponseHandler = {
    case ToggleDoesNotExist(id) =>
      publishFailure(actionId, id, "reason" -> "toggle does not exist")
      NotFound(errorJson("Not found", s"A toggle with id $id does not exist", "Provide an existing toggle id"))

    case r => handler(r)
  }

  def whenPersisted(handler: ResponseHandler)(implicit actionId: String): ResponseHandler = {
    case PersistFailed(toggleId, cause) =>
      publishFailure(actionId, cause, "toggleId" -> toggleId)
      InternalServerError(errorJson("Internal server error", cause.getMessage))

    case r => handler(r)
  }
}
