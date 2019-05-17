package org.encryfoundation.generator.network

import java.net.InetSocketAddress
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.network.NetworkServer.CheckConnection
import org.encryfoundation.generator.network.PeerHandler.{AddPeerToConnectionList, AddedToConnectionListSuccessfully}
import org.encryfoundation.generator.utils.Settings
import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

class NetworkServer(settings: Settings) extends Actor with StrictLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.dispatcher

  var isConnected: Boolean = false

  var connectedPeers: Map[InetSocketAddress, ActorRef] = Map.empty

  IO(Tcp) ! Bind(self, settings.network.bindAddress)

  override def receive: Receive = {
    case Bound(localAddress) =>
      logger.info(s"Local app was successfully bound to $localAddress!")
      context.system.scheduler.schedule(5.seconds, 30.seconds, self, CheckConnection)

    case CommandFailed(_: Bind) =>
      logger.info(s"Failed to bind to ${settings.network.bindAddress}.")
      context.stop(self)

    case Connected(remote, _) =>
      val handler: ActorRef = context.actorOf(PeerActor.props(remote, sender, self))
      println(s"Successfully connected to $remote. Creating handler: $handler.")
      sender ! Register(handler)
      sender ! ResumeReading

    case CommandFailed(c: Connect) =>
      isConnected = false
      logger.info(s"Failed to connect to: ${c.remoteAddress}")

    case CheckConnection if !isConnected =>
      IO(Tcp) ! Connect(settings.network.connectedPeer)
      logger.info(s"Trying to connect to ${settings.network.connectedPeer}.")

    case CheckConnection =>

    case AddPeerToConnectionList(peer) =>
      connectedPeers += (peer -> sender())
      sender() ! AddedToConnectionListSuccessfully

    case msg => logger.info(s"Got strange message on NetworkServer: $msg.")
  }
}

object NetworkServer {

  case object CheckConnection

  def props(settings: Settings): Props = Props(new NetworkServer(settings))
}