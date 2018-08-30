package org.encryfoundation.generator.utils

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

case class Settings(peers: List[Node], nodePollingInterval: Int)

case class Node(host: String, port: Int)

object Settings {
  def load: Settings = ConfigFactory.load("local.conf")
    .withFallback(ConfigFactory.load).as[Settings]
}