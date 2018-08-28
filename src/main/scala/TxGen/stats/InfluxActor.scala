package TxGen.stats

import akka.actor.Actor
import TxGen.TxGenerator.settings
import org.influxdb.{InfluxDB, InfluxDBFactory}
import TxGen.stats.InfluxActor.TestMessage

class InfluxActor extends Actor {

  val influxDB: InfluxDB =
    InfluxDBFactory.connect(settings.influxDB.url, settings.influxDB.login, settings.influxDB.password)

  influxDB.setRetentionPolicy("autogen")

  override def preStart(): Unit =
    influxDB.write(settings.influxDB.udpPort, s"""nodestarttime1 value="${settings.txSettings.name}"""")

  override def receive: Receive = {
    case TestMessage() =>
  }
}

object InfluxActor {
  case class TestMessage()
}
