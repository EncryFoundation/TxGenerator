package org.encryfoundation.generator.actors

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.InfluxActor.{RequestedFromLocalOutputs, RequestedFromRemoteOutputs}
import org.influxdb.{InfluxDB, InfluxDBFactory}
import org.encryfoundation.generator.GeneratorApp.settings
import java.net._

class InfluxActor extends Actor with StrictLogging {

  val influxDB: InfluxDB =
    InfluxDBFactory.connect(settings.influxDB.url, settings.influxDB.login, settings.influxDB.password)
  val nodeName: String = InetAddress.getLocalHost.getHostAddress
  val udpPort: Int = settings.influxDB.udpPort

  influxDB.setRetentionPolicy("autogen")

  override def preStart(): Unit = {
    logger.info("Influx actor started")
  influxDB.write(settings.influxDB.udpPort, s"""txGenStartTime value="$nodeName"""")
  }

  override def receive: Receive = {
    case RequestedFromLocalOutputs(pool, utxosNum) =>
      influxDB.write(udpPort, s"requestUtxos,nodeName=$nodeName value=$utxosNum,poolSize=$pool")
    case RequestedFromRemoteOutputs(pool) =>
      influxDB.write(udpPort, s"remoteUtxos,nodeName=$nodeName value=$pool")
  }
}

object InfluxActor {

  case class RequestedFromLocalOutputs(currentPool: Int, numOfUtxos: Int)

  case class RequestedFromRemoteOutputs(pool: Int)
}