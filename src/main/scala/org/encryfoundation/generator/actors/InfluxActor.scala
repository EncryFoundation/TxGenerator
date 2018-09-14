package org.encryfoundation.generator.actors

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.InfluxActor.{IncomeOutputsMessage, RequestUtxoMessage, SendTxsQty, TestMessage}
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
    logger.info("Start influx actor")
    influxDB.write(settings.influxDB.udpPort, s"""nodestarttime1 value="$nodeName"""")
  }

  override def receive: Receive = {
    case IncomeOutputsMessage(outputs, poolSize) =>
      influxDB.write(udpPort, s"incomeOutput,nodeName=$nodeName value=$outputs,poolSize=$poolSize")
    case RequestUtxoMessage(pool, utxoNum) =>
      influxDB.write(udpPort, s"requestUtxos,nodeName=$nodeName value=$pool,requestedUtxos=$utxoNum")
    case SendTxsQty(qty) =>
      influxDB.write(udpPort, s"sendTxsQty,nodeName=$nodeName value=$qty")
  }
}

object InfluxActor {

  case class TestMessage()

  case class IncomeOutputsMessage(outputsQty: Int, poolSize: Int)

  case class RequestUtxoMessage(currentPool: Int, numOfUtxos: Int)

  case class WorkerMessage(workerName: String)

  case class SendTxsQty(qty: Int)

}

