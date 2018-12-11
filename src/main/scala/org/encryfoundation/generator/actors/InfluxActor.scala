package org.encryfoundation.generator.actors

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.StrictLogging
import org.influxdb.{InfluxDB, InfluxDBFactory}
import java.net._
import org.encryfoundation.generator.utils.Settings

class InfluxActor(settings: Settings) extends Actor with StrictLogging {

  val nodeName: String   = InetAddress.getLocalHost.getHostAddress
  val udpPort: Int       = settings.influxDB.map(_.udpPort).getOrElse(0)
  val influxDB: InfluxDB = InfluxDBFactory.connect(
    settings.influxDB.map(_.url).getOrElse(""),
    settings.influxDB.map(_.login).getOrElse(""),
    settings.influxDB.map(_.password).getOrElse("")
  )
  influxDB.setRetentionPolicy("autogen")

  override def preStart(): Unit = {
    logger.info("Influx actor started")
    influxDB.write(udpPort, s"""txGenStartTime value="$nodeName"""")
  }

  override def receive: Receive = {
    case _ =>
  }
}

object InfluxActor {
  def props(settings: Settings) = Props(new InfluxActor(settings))
}