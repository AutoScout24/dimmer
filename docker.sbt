import com.typesafe.sbt.packager.docker._

// *** Docker container contents ***

// Remove dev and test configs
mappings in Docker := {
    val dockerMappings = (mappings in Docker).value

    def isConfFile(file: File) = file.getParentFile.getName == "conf"

    val excludedPrefixes = Seq("dev", "test")

    dockerMappings.filterNot {
        case (file, _) =>
          isConfFile(file) && excludedPrefixes.exists(p => file.getName.startsWith(p))
    }
}


// *** Dockerfile settings ***

// Using openjdk base image due to licensing issues of Oracle Java.
dockerBaseImage    := "java:openjdk-8-jre-alpine"

dockerExposedPorts := Seq(9000, ConfigurationSettings.JmxPort)

dockerCmd          := ConfigurationSettings.Run

// installing Bash for native packager run script
val installBash = Cmd("RUN", "apk add --update bash && rm -rf /var/cache/apk/*")

// define health check
val healthCheck = Cmd("HEALTHCHECK", "--interval=5s --timeout=3s --retries=3 " +
                      "CMD wget -nv http://localhost:9000/healthcheck || exit 1")

// move application.conf to /opt/docker/etc
val etcDir   = "/opt/docker/etc"
val confDir  = "/opt/docker/conf"
val appConf  = "application.conf"
val moveConf = Cmd("RUN", Seq(s"mkdir -p $etcDir",
                             s"mv $confDir/$appConf $etcDir/$appConf",
                             s"ln -s $etcDir/$appConf $confDir/$appConf").mkString("; "))

// add commands standard Dockerfile commands
dockerCommands := {
  val oldCmds = dockerCommands.value
  val fromCmd = oldCmds.head
  val stdCmds = oldCmds.drop(1).dropRight(1)
  val runCmd  = oldCmds.last

  Seq( fromCmd,
       installBash ) ++
       stdCmds ++
  Seq( healthCheck,
       moveConf,
       runCmd )
}


// *** Docker publishing settings ***

dockerUpdateLatest := false

dockerRepository   := Some("as24")

version in Docker  := version.value
