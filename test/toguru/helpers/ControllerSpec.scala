package toguru.helpers

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.mvc.Results

trait ControllerSpec extends PlaySpec with Results with MockitoSugar with AuthorizationHelpers with DisabledLogging
