// *** sbt build libraries ***

resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver.sbtPluginRepo("releases")

// include to compile protocol buffers without installed protobuf binary.
libraryDependencies ++= Seq(
  "com.github.os72" % "protoc-jar" % "3.0.0-b2.1"
)


// *** Plugins ***

addSbtPlugin("com.typesafe.sbt"       % "sbt-native-packager" % "1.2.0-M5")
addSbtPlugin("com.typesafe.play"      % "sbt-plugin"          % "2.5.4")
addSbtPlugin("com.trueaccord.scalapb" % "sbt-scalapb"         % "0.5.38")
addSbtPlugin("org.scoverage"          % "sbt-scoverage"       % "1.3.5")
addSbtPlugin("org.scoverage"          % "sbt-coveralls"       % "1.1.0")
