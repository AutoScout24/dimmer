package toguru.filters

import javax.inject.Inject

import akka.stream.Materializer
import com.kenshoo.play.metrics.Metrics
import play.api.http.{DefaultHttpFilters, HttpFilters}
import play.api.mvc._
import play.filters.cors.CORSFilter
import play.filters.gzip.GzipFilter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Filters @Inject()(gzip: GzipFilter, counter: ErrorCounterFilter, corsFilter: CORSFilter) extends HttpFilters {
  val filters = Seq(gzip, counter, corsFilter)
}

class ErrorCounterFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext, metrics: Metrics) extends Filter {

  val serverErrors = metrics.defaultRegistry.counter("server-errors")
  val clientErrors = metrics.defaultRegistry.counter("client-errors")

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] =
    nextFilter(requestHeader).andThen {
      case Failure(_)      => serverErrors.inc()
      case Success(result) => result.header.status match {
        case s if s >= 500 => serverErrors.inc()
        case s if s >= 400 => clientErrors.inc()
        case _             => ()
      }
    }
}