import sbt._

object Version {

  val ScalaPb                 = "0.5.38"
  val Play                    = "2.5.10"
  val NativePackager          = "1.2.0-M5"
  val AkkaPersistenceJdbc     = "2.6.12"
  val AkkaPersistenceInmemory = "1.3.7"
  val Postgres                = "9.4.1209"
  val LogstashEncoder         = "4.7"
  val ScalaTestPlus           = "1.5.1"
  val PlayMetrics             = "2.5.13"
  val DropwizardMetrics       = "3.1.2"
  val Mockito                 = "1.10.19"

}

object Library {

  val AkkaPersistenceJdbc     = "com.github.dnvriend"    %% "akka-persistence-jdbc"     % Version.AkkaPersistenceJdbc
  val AkkaPersistenceInmemory = "com.github.dnvriend"    %% "akka-persistence-inmemory" % Version.AkkaPersistenceInmemory
  val Postgres                = "org.postgresql"         %  "postgresql"                % Version.Postgres
  val LogstashEncoder         = "net.logstash.logback"   %  "logstash-logback-encoder"  % Version.LogstashEncoder
  val ScalaPbRuntime          = "com.trueaccord.scalapb" %% "scalapb-runtime"           % Version.ScalaPb
  val ScalaTestPlus           = "org.scalatestplus.play" %% "scalatestplus-play"        % Version.ScalaTestPlus
  val PlayMetrics             = "de.threedimensions"     %% "metrics-play"              % Version.PlayMetrics
  val DropwizardMetrics       =  "io.dropwizard.metrics" %  "metrics-core"              % Version.DropwizardMetrics exclude("commons-logging", "commons-logging")
  val Mockito                 = "org.mockito"            %  "mockito-core"              % Version.Mockito

}
