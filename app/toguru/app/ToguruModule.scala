package toguru.app

import javax.inject.Singleton

import akka.actor.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import com.google.inject.{AbstractModule, Provides}
import play.api.libs.concurrent.AkkaGuiceSupport
import slick.backend.DatabaseConfig
import slick.driver.JdbcDriver
import toguru.toggles.{AuditLog, AuditLogActor, ToggleState, ToggleStateActor}

class ToguruModule extends AbstractModule with AkkaGuiceSupport {

  def configure() = {
    bind(classOf[ToguruServerMetrics]).asEagerSingleton()
    bind(classOf[Config]).to(classOf[Configuration]).asEagerSingleton()

    bindActor[HealthActor]("health")
    bindActor[ToggleStateActor]("toggle-state")
    bindActor[AuditLogActor]("audit-log")
  }

  @Provides @Singleton
  def auditLogConfig(config: Config): AuditLog.Config = config.auditLog

  @Provides @Singleton
  def toggleStateConfig(config: Config): ToggleState.Config = config.toggleState

  @Provides @Singleton
  def dbConfig: DatabaseConfig[JdbcDriver] = DatabaseConfig.forConfig("slick")

  @Provides @Singleton
  def readJournal(system: ActorSystem): JdbcReadJournal =
    PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
}
