package org.encryfoundation.generator

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.{Generator, InfluxActor}
import org.encryfoundation.generator.utils.{NetworkTimeProvider, Settings}
import scala.concurrent.ExecutionContextExecutor
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.encryfoundation.generator.network.NetworkServer
import org.encryfoundation.generator.utils.Mnemonic._

object GeneratorApp extends App with StrictLogging {

  implicit lazy val system: ActorSystem             = ActorSystem()
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val ec: ExecutionContextExecutor    = system.dispatcher
  val settings: Settings                            = ConfigFactory.load("local.conf")
                                                      .withFallback(ConfigFactory.load()).as[Settings]
  val influx: Option[ActorRef] =
    settings.influxDB.map(_ => system.actorOf(InfluxActor.props(settings), "influxDB"))

  val timeProvider: NetworkTimeProvider = new NetworkTimeProvider(settings.ntp)

  val networkServer: ActorRef = system.actorOf(NetworkServer.props(settings, timeProvider))

  settings.peers.foreach { peer =>
    logger.info(s"Created generator actor for ${peer.explorerHost}:${peer.explorerPort}.")
    system.actorOf(
      Generator.props(settings, createPrivKey(Some(peer.mnemonicKey)), peer, influx, networkServer), peer.explorerHost)
  }
}