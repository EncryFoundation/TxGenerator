package org.encryfoundation.generator.settings

import java.io.File
import java.net.InetSocketAddress
import net.ceedubs.ficus.readers.ValueReader
import com.typesafe.config.Config

case class GeneratorSettings(network: NetworkSettings)

object GeneratorSettings {
  implicit val fileReader: ValueReader[File] = (cfg, path) => new File(cfg.getString(path))
  implicit val byteValueReader: ValueReader[Byte] = (cfg, path) => cfg.getInt(path).toByte
  implicit val inetSocketAddressReader: ValueReader[InetSocketAddress] = { (config: Config, path: String) =>
    val split: Array[String] = config.getString(path).split(":")
    new InetSocketAddress(split(0), split(1).toInt)
  }
}
