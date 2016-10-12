package toguru.app

import scala.collection.JavaConversions._
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import com.typesafe.config.{ConfigList, ConfigObject, Config => TypesafeConfig}
import play.api.Logger
import toguru.toggles.Authentication
import toguru.toggles.Authentication.ApiKey

import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

trait Config {

  def auth: Authentication.Config

  val typesafeConfig: TypesafeConfig

  val actorTimeout: FiniteDuration
}

@Singleton
class Configuration @Inject() (playConfig: play.api.Configuration) extends Config {

  Logger.info(s"Using config ${sys.props.get("config.resource").mkString}")

  override lazy val typesafeConfig = playConfig.underlying

  override val actorTimeout: FiniteDuration = typesafeConfig.getDuration("actorTimeout",TimeUnit.MILLISECONDS).milliseconds

  override val auth = {
    def parseApiKeys(list: ConfigList): List[ApiKey] = list.flatMap {
      case value : ConfigObject =>
        val config = value.toConfig
        try {
          Some(ApiKey(config.getString("name"), config.getString("key")))
        } catch {
          case NonFatal(e) =>
            Logger.warn("Parsing api key failed", e)
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