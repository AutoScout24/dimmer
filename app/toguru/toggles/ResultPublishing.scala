package toguru.toggles

import toguru.logging.EventPublishing

trait ResultPublishing extends EventPublishing {

  def publishSuccess(actionId: String, toggleId: String): Unit =
    publisher.event(s"$actionId-success", "toggleId" -> toggleId)

  def publishFailure(actionId: String, toggleId: String, fields: (String, Any)*): Unit =
    publisher.event(s"$actionId-failure", ("toggleId" -> toggleId) +: fields : _*)

  def publishFailure(actionId: String, toggleId: String, cause: Throwable): Unit =
    publisher.event(s"$actionId-failure", "cause" -> cause, "toggleId" -> toggleId)

}
