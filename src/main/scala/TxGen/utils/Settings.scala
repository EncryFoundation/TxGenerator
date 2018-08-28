package TxGen.utils

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

case class Settings(influxDB: InfluxDBSettings,
                    txSettings: TxSettings)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int,
                            enable: Boolean)

case class TxSettings(name: String)

object Settings {
  def load: Settings = ConfigFactory.load("local.conf")
    .withFallback(ConfigFactory.load).as[Settings]
}
