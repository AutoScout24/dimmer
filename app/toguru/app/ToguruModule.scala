package toguru.app

import javax.inject.Singleton

import akka.actor.ActorSystem
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import com.google.inject.{AbstractModule, Provides}
import play.api.libs.concurrent.AkkaGuiceSupport
import slick.backend.DatabaseConfig
import slick.driver.JdbcDriver

class ToguruModule extends AbstractModule with AkkaGuiceSupport {

  def configure() = {
    bind(classOf[JmxReportingSetup]).asEagerSingleton()
    bind(classOf[Config]).to(classOf[Configuration]).asEagerSingleton()

    bindActor[HealthActor]("health")
  }

  @Provides @Singleton
  def dbConfig: DatabaseConfig[JdbcDriver] = DatabaseConfig.forConfig("slick")

  @Provides @Singleton
  def readJournal(system: ActorSystem): JdbcReadJournal =
    PersistenceQuery(system).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)
}
