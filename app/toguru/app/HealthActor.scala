package toguru.app

import javax.inject.{Inject, Named}

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import toguru.app.HealthActor.{CheckHealth, GetHealth, HealthStatus}
import toguru.helpers.FutureTimeout
import toguru.toggles.ToggleStateActor.GetState
import toguru.toggles.ToggleStates

import scala.concurrent.duration._

object HealthActor {

  case class CheckHealth()
  case class GetHealth()
  case class HealthStatus(databaseHealthy: Boolean, toggleStateHealthy: Boolean) {
    val ready = databaseHealthy && toggleStateHealthy

    val healthy = toggleStateHealthy
  }
}


class HealthActor @Inject() (dbConfig: DatabaseConfig[JdbcProfile], @Named("toggle-state") toggleState: ActorRef, config: Config) extends Actor with FutureTimeout {

  var databaseHealthy: Boolean = false

  var toggleStateHealthy: Boolean = false

  implicit val actorTimeout = Timeout(config.actorTimeout)

  val repeatedlyHealthCheck = {
    implicit val ec = context.system.dispatcher

    context.system.scheduler.schedule(0.seconds, 1.second, self, CheckHealth())
  }

  override def postStop() = repeatedlyHealthCheck.cancel()

  def checkHealth(): Unit = {
    import dbConfig.driver.api._
    implicit val ec = context.dispatcher
    implicit val scheduler = context.system.scheduler

    timeout(500.millis, dbConfig.db.run(sql"""SELECT 1""".as[Int].head))
      .map(result => databaseHealthy = result == 1)
      .recover { case _ => databaseHealthy = false }


    (toggleState ? GetState).map {
      case _ : ToggleStates => toggleStateHealthy = true
      case _ => toggleStateHealthy = false
    }.recover { case _ => toggleStateHealthy = false }
  }

  override def receive = {
    case CheckHealth() => checkHealth()
    case GetHealth() => sender ! HealthStatus(databaseHealthy, toggleStateHealthy)
  }
}