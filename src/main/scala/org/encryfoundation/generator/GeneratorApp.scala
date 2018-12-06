package org.encryfoundation.generator

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.{Generator, InfluxActor}
import org.encryfoundation.generator.transaction.Account
import org.encryfoundation.generator.utils.Settings
import org.encryfoundation.generator.wallet.{WalletStorage, WalletStorageReader}
import scala.concurrent.ExecutionContextExecutor
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._

object GeneratorApp extends App with StrictLogging {

  implicit lazy val system: ActorSystem = ActorSystem()
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val ec: ExecutionContextExecutor = system.dispatcher
  val settings: Settings =
    ConfigFactory.load("local.conf").withFallback(ConfigFactory.load).as[Settings]

  if (settings.influxDB.enable) system.actorOf(Props[InfluxActor], "influxDB")

  logger.info("Transaction generator have been started.")

  val walletStorage: WalletStorage = WalletStorageReader(settings).createWalletStorage

  val generators: Seq[ActorRef] = Account.parseFromSettings(settings.accountSettings).zipWithIndex
    .map { case (account, idx) =>
      system.actorOf(Generator.props(settings, account, walletStorage), s"generator-$idx")
    }
}