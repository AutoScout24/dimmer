package toguru.toggles

import org.scalatestplus.play.PlaySpec
import play.api.mvc.Result
import toguru.toggles.ToggleActor.{PersistFailed, Success, ToggleDoesNotExist}

class ToggleActorResponsesSpec extends PlaySpec {

  trait Setup extends ToggleActorResponses {

    val handler: ResponseHandler = { _ => Ok("")}

    val persistHandler: ResponseHandler = whenPersisted(handler)("my-action")

    val existsHandler: ResponseHandler = whenToggleExists(handler)("my-action")

    val failure = PersistFailed("my-toggle", new RuntimeException("test exception"))

    val doesntExist = ToggleDoesNotExist("my-toggle")
  }

  "whenPersisted" should {
    "return internal server error on persistence errors" in new Setup {

      val response: Result = persistHandler.apply(failure)

      response.header.status mustBe 500
    }

    "pass message to handler on success" in new Setup {

      val response: Result = persistHandler.apply(Success)

      response.header.status mustBe 200
    }
  }

  "whenToggleExists" should {
    "return internal server error on persistence errors" in new Setup {

      val response: Result = existsHandler.apply(doesntExist)

      response.header.status mustBe 404
    }

    "pass message to handler on success" in new Setup {

      val response: Result = existsHandler.apply(Success)

      response.header.status mustBe 200
    }
  }

}
