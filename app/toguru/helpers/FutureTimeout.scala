package toguru.helpers

import java.util.concurrent.TimeoutException

import akka.actor.Scheduler
import akka.pattern.after

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

object FutureTimeout extends FutureTimeout {

}

trait FutureTimeout {

  def timeout[T](deadline: FiniteDuration, future: Future[T])(implicit ec: ExecutionContext, scheduler: Scheduler) : Future[T] = {
    val timeoutFuture = after(deadline, scheduler) { Future.failed(new TimeoutException()) }
    Future.firstCompletedOf(List(future, timeoutFuture))
  }
}
