package toguru.toggles

import org.scalatest.MustMatchers

import scala.annotation.tailrec
import scala.concurrent.duration._

trait WaitFor {

  self: MustMatchers =>

  def waitFor(timeout: FiniteDuration, checkEvery: FiniteDuration = 100.millis)(test: => Boolean): Unit = {
    val deadline = System.currentTimeMillis() + timeout.toMillis

    @tailrec
    def tryTest(): Boolean =
      if(test)
        true
      else if(System.currentTimeMillis() + checkEvery.toMillis >= deadline)
        false
      else {
        Thread.sleep(checkEvery.toMillis)
        tryTest()
      }

    tryTest() mustBe true
  }

}
