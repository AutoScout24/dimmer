object ConfigurationSettings {

  val JmxPort = 1199

  // options for local JMX access on port 1099 without authentication and ssl.
  val JmxOptions = Seq(
     "-Dcom.sun.management.jmxremote",
    s"-Dcom.sun.management.jmxremote.port=$JmxPort",
    s"-Dcom.sun.management.jmxremote.rmi.port=$JmxPort",
     "-Dcom.sun.management.jmxremote.authenticate=false",
     "-Dcom.sun.management.jmxremote.ssl=false",
     "-Djava.rmi.server.hostname=127.0.0.1"
  )

  val Run  = Seq("-Dconfig.resource=application.conf", "-Dlogger.resource=logger-config.xml") ++ JmxOptions
  val Test = Seq("-Dconfig.resource=test.conf",        "-Dlogger.resource=logger-config.xml") ++ JmxOptions

}