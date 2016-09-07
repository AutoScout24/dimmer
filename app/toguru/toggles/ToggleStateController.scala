package toguru.toggles

import play.api.mvc.Controller
import toguru.logging.EventPublishing

class ToggleStateController extends Controller with EventPublishing with JsonResponses{


  def get = ActionWithJson.async { request =>
    ???
  }
}
