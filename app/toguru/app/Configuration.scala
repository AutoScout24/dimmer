package toguru.app

import scala.collection.JavaConversions._
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import com.typesafe.config.{ConfigList, ConfigObject, Config => TypesafeConfig}
import play.api.Logger
import toguru.toggles.{AuditLog, Authentication, ToggleState}
import toguru.toggles.Authentication.ApiKey

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

trait Config {

  def auth: Authentication.Config

  val typesafeConfig: TypesafeConfig

  val actorTimeout: FiniteDuration

  def toggleState: ToggleState.Config

  def auditLog: AuditLog.Config
}

@Singleton
class Configuration @Inject() (playConfig: play.api.Configuration) extends Config {

  Logger.info(s"Using config ${sys.props.get("config.resource").mkString}")

  override lazy val typesafeConfig = playConfig.underlying

  override val actorTimeout: FiniteDuration = typesafeConfig.getDuration("actorTimeout",TimeUnit.MILLISECONDS).milliseconds

  override val auditLog = {
    val retentionTime   = playConfig.getMilliseconds("auditLog.retentionTime").getOrElse(90.days.toMillis).milliseconds
    val retentionLength = playConfig.getInt("auditLog.retentionLength").getOrElse(10000)
    AuditLog.Config(retentionTime, retentionLength)
  }

  override val toggleState = {
    val initialize = playConfig.getBoolean("toggleState.initializeOnStartup").getOrElse(false)
    ToggleState.Config(initialize)
  }

  override val auth = {
    def parseApiKeys(list: ConfigList): List[ApiKey] = list.flatMap {
      case value : ConfigObject =>
        val config = value.toConfig
        Try(ApiKey(config.getString("name"), config.getString("hash"))) match {
          case Success(key) => Some(key)
          case Failure(ex) =>
            Logger.warn("Parsing api key failed", ex)
            None
        }
    }.toList

    val disabled = Try(typesafeConfig.getBoolean("auth.disabled")).getOrElse(false)

    if(disabled)
      Logger.info("Authentication is disabled.")
    else
      Logger.info("Authentication is enabled.")

    val apiKeys =
      Try(typesafeConfig.getList("auth.api-keys"))
        .map(parseApiKeys)
        .getOrElse(Seq.empty)

    Authentication.Config(apiKeys, disabled)
  }
}