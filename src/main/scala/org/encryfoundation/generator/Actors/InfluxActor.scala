package org.encryfoundation.generator.Actors

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.Actors.InfluxActor.TestMessage
import org.influxdb.{InfluxDB, InfluxDBFactory}
import org.encryfoundation.generator.GeneratorApp.settings

class InfluxActor extends Actor with StrictLogging {

  val influxDB: InfluxDB =
    InfluxDBFactory.connect(settings.influxDB.url, settings.influxDB.login, settings.influxDB.password)

  influxDB.setRetentionPolicy("autogen")

  override def preStart(): Unit = {
    logger.info("Start influx actor")
    influxDB.write(settings.influxDB.udpPort, s"""nodestarttime1 value="${settings.txSettings.name}"""")
  }

  override def receive: Receive = {
    case TestMessage() =>
  }
}

object InfluxActor {

  case class TestMessage()

}

