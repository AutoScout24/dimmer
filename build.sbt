// *** Coordinates ***

organization := "com.autoscout24"
name         := "toguru"
version      := Option(System.getenv("TOGURU_VERSION")).getOrElse("local")


// *** Libraries ***

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/maven-releases/",
  Resolver.jcenterRepo
)

import Library._
import com.trueaccord.scalapb.{ScalaPbPlugin => PB}

libraryDependencies ++= Seq(
  ws,
  filters,
  AkkaPersistenceJdbc,
  Postgres,
  LogstashEncoder,
  PlayMetrics,
  DropwizardMetrics,
  ScalaBcrypt,
  ScalaPbRuntime          % PB.protobufConfig,
  ScalaTestPlus           % Test,
  AkkaPersistenceInmemory % Test,
  Mockito                 % Test
)


// *** Runtime configurations ***

fork := true

envVars in Test ++= Map(
  "POSTGRES_HOST" -> "127.0.0.1",
  "POSTGRES_PORT" -> ConfigurationSettings.freePort.toString
)

javaOptions in Runtime ++= ConfigurationSettings.Dev

javaOptions in Test    ++= ConfigurationSettings.Test

// *** Play configuration settings ***

routesGenerator := InjectedRoutesGenerator


// *** Compilation and packaging ***

scalaVersion in ThisBuild := "2.11.8"

scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature",
  "-Xfatal-warnings", "-Xmax-classfile-name", "130")

// Don't package API docs in dist
doc in Compile <<= target.map(_ / "none")

// Remove toplevel directory containing service version
topLevelDirectory := None


// *** Protobuf settings ***

// load default settings.
PB.protobufSettings

// look for .proto files in /conf/protobuf
sourceDirectory in PB.protobufConfig := baseDirectory.value / "conf" / "protobuf"

// add generated source files to Scala source path
scalaSource in PB.protobufConfig <<= (sourceManaged in Compile)

// use Protocol Buffers version 3
version in PB.protobufConfig := "3.0.0"
PB.runProtoc in PB.protobufConfig := (args => com.github.os72.protocjar.Protoc.runProtoc("-v300" +: args.toArray))


// *** Integration tests settings ***

lazy val ItTest = config("it") extend(Test)

def itSpecFilter(name: String): Boolean = name.endsWith("IntegrationSpec")
def specFilter(name: String): Boolean = name.endsWith("Spec") && !itSpecFilter(name)

testOptions in ItTest := Seq(Tests.Filter(itSpecFilter))

testOptions in Test := Seq(Tests.Filter(specFilter))


// *** Test coverage settings ***

coverageMinimum := 80

coverageFailOnMinimum := true

coverageExcludedPackages := Seq(
  """router""",                         // generated code
  """toguru\.toggles\.events\..*""",    // generated code
  """toguru\.toggles\.snapshots\..*""", // generated code
  """.*ReverseApplication""",           // generated code
  """.*Reverse.*Controller""",          // generated code
  """toguru\.filters\..*""",            // low test value and hard to test
  """toguru\.app\.ErrorHandler"""       // low test value and hard to test
).mkString(";")

// *** Plugins and configs ***

lazy val root = (project in file("."))
  .enablePlugins(SbtWeb, PlayScala, DockerPlugin)
  .configs(ItTest)
  .settings(inConfig(ItTest)(Defaults.testTasks) : _*)
