package toguru.helpers

import java.io.IOException
import java.sql.{DriverManager, SQLException}

import com.typesafe.config.Config

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.sys.process._
import scala.util.Random

trait PostgresSetup {

  def log(message: String): Unit
  def config: Config

  final def waitForPostgres(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    def waitForConnection(): Unit = {
      try {
        val url = config.getString("slick.db.url")
        val conn = DriverManager.getConnection(url)
        try {
          val query = config.getString("slick.db.connectionTestQuery")
          val statement = conn.createStatement()
          val rs = statement.executeQuery(query)
          rs.next()
          log("Postgres is ready.")
        } finally {
          conn.close()
        }
      } catch {
        case _ : IOException  =>
          Thread.sleep(500)
          waitForConnection()
        case _ : SQLException =>
          Thread.sleep(500)
          waitForConnection()
      }
    }
    log("Waiting for Postgres...")
    Await.result(Future { waitForConnection() }, 60.seconds)
  }

  var maybePostgresName:Option[String] = None

  def startPostgres() = {

    def run(prefix: String, command: String): Int = {
      val logger = ProcessLogger(line => log(s"$prefix: $line"), line => log(s"$prefix: $line"))
      Process(command).run(logger).exitValue()
    }


    val postgresDockerImage = "postgres:local"
    val suffix = {
      val n = Random.nextLong
      (if (n == Long.MinValue) 0 else Math.abs(n)).toString.take(8)
    }

    val postgresName = s"postgres-$suffix"
    maybePostgresName = Some(postgresName)

    import scala.concurrent.ExecutionContext.Implicits.global
    Future {
      val buildExitValue = run("Postgres build", s"docker build -t $postgresDockerImage -f Dockerfile.Postgres .")

      if(buildExitValue != 0)
        throw new RuntimeException("Docker build failed")

      val imageFilter = s"ancestor=$postgresDockerImage"

      val runningContainers = s"docker ps --filter $imageFilter --filter status=running -q".!!.trim.split("\n").filter(_.nonEmpty)
      if (runningContainers.nonEmpty) {
        log(s"Stopping running Postgres container(s) ${runningContainers.mkString(", ")}...")
        Process(s"docker stop ${runningContainers.mkString(" ")}").!!
      }

      val port = config.getString("slick.db.port")
      val exitValue = run("Postgres start", s"docker run --name $postgresName -d -p $port:5432 $postgresDockerImage")

      if(exitValue != 0)
        throw new RuntimeException("Starting docker container failed")

    }
  }

  def stopPostgres() = {
    import scala.concurrent.ExecutionContext.Implicits.global
    for (postgresName <- maybePostgresName) {
      val discardOutput = new ProcessIO(_.close, _.close, _.close)
      val kill = Process(s"docker kill $postgresName").run(discardOutput)
      Await.result(Future(kill.exitValue()), 10.seconds)
      val rm = Process(s"docker rm $postgresName").run(discardOutput)
      Await.result(Future(rm.exitValue()), 10.seconds)
    }
    maybePostgresName = None
  }


}
