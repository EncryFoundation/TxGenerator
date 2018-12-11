package org.encryfoundation.generator

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.{Generator, InfluxActor}
import org.encryfoundation.generator.utils.Settings
import org.encryfoundation.generator.wallet.WalletStorageReader
import scala.concurrent.ExecutionContextExecutor
import com.typesafe.config.ConfigFactory
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

object GeneratorApp extends App with StrictLogging {

  implicit lazy val system: ActorSystem             = ActorSystem()
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val ec: ExecutionContextExecutor    = system.dispatcher
  val settings: Settings                            = ConfigFactory.load("local.conf")
                                                      .withFallback(ConfigFactory.load()).as[Settings]
  val walletStorageReader: WalletStorageReader      = WalletStorageReader(settings)
  val privateKeys: List[PrivateKey25519]            = walletStorageReader.accounts

  settings.influxDB.foreach(_ => system.actorOf(InfluxActor.props(settings), "influxDB"))
  privateKeys.zipWithIndex.map { case (privKey, idx) =>
      logger.info(s"New generator actor started with privKey: ${Algos.encode(privKey.bytes)}.")
      system.actorOf(Generator.props(settings, privKey, walletStorageReader), s"generator-$idx")
    }
}