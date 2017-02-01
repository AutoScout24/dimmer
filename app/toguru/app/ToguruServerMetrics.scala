package toguru.app

import javax.inject.{Inject, Named}

import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask
import com.codahale.metrics.{Gauge, JmxReporter}
import com.kenshoo.play.metrics.Metrics
import toguru.toggles.ToggleStateActor.GetSeqNo

import scala.concurrent.Await


class ToguruServerMetrics @Inject()(metrics: Metrics, @Named("toggle-state") toggleState: ActorRef, config: Config) {
  val reporter = JmxReporter.forRegistry(metrics.defaultRegistry).build()
  reporter.start()

  metrics.defaultRegistry.register("sequence-no-gauge", new Gauge[Long] {
    implicit val timeout = Timeout(config.actorTimeout)

    override def getValue = Await.result[Long]((toggleState ? GetSeqNo).mapTo[Long], config.actorTimeout)
  })
}