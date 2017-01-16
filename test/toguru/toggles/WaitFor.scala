package toguru.toggles

import org.scalatest.MustMatchers

import scala.concurrent.duration._

trait WaitFor {

  self: MustMatchers =>

  def waitFor(times: Int, wait: FiniteDuration = 1.second)(test: => Boolean): Unit = {
    val success = (1 to times).exists { i =>
      if(test) {
        true
      } else {
        if(i < times)
          Thread.sleep(wait.toMillis)
        false
      }
    }

    success mustBe true
  }

}
