package org.encryfoundation.generator

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.{Generator, InfluxActor}
import org.encryfoundation.generator.utils.Settings
import org.encryfoundation.generator.wallet.{WalletStorage, WalletStorageReader}
import scala.concurrent.ExecutionContextExecutor
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519

object GeneratorApp extends App with StrictLogging {

  implicit lazy val system: ActorSystem = ActorSystem()
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val ec: ExecutionContextExecutor = system.dispatcher
  val settings: Settings =
    ConfigFactory.load("local.conf").withFallback(ConfigFactory.load()).as[Settings]

  if (settings.influxDB.enable) system.actorOf(Props[InfluxActor], "influxDB")

  logger.info("Transaction generator have been started.")

  val walletStorageReader: WalletStorageReader = WalletStorageReader(settings)

  val walletStorage: WalletStorage = walletStorageReader.createWalletStorage

  val privateKeys: List[PrivateKey25519] = walletStorageReader.accounts

  val generators: Seq[ActorRef] = privateKeys.zipWithIndex
    .map { case (privKey, idx) =>
      logger.info(s"New generator actor started with privKey: ${Algos.encode(privKey.bytes)}.")
      system.actorOf(Generator.props(settings, privKey, walletStorage), s"generator-$idx")
    }
}