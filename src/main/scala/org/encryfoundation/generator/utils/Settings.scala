package org.encryfoundation.generator.utils

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

case class Settings(peers: List[Node],
                    nodePollingInterval: Int,
                    influxDB: InfluxDBSettings,
                    txSettings: TxSettings)

case class Node(host: String, port: Int)

case class TxSettings(name: String)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int,
                            enable: Boolean)

object Settings {
  def load: Settings = ConfigFactory.load("local.conf")
    .withFallback(ConfigFactory.load).as[Settings]
}