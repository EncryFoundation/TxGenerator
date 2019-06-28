package org.encryfoundation.generator.network

import java.net.InetSocketAddress
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp.SO.KeepAlive
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.Generator
import org.encryfoundation.generator.actors.Generator.TransactionForCommit
import org.encryfoundation.generator.network.BasicMessagesRepo.{InvNetworkMessage, Outgoing}
import org.encryfoundation.generator.network.NetworkMessagesHandler.BroadcastInvForTx
import org.encryfoundation.generator.network.NetworkServer.{CheckConnection, ConnectionSetupSuccessfully}
import org.encryfoundation.generator.network.PeerHandler._
import org.encryfoundation.generator.modifiers.Transaction
import org.encryfoundation.generator.utils.CoreTaggedTypes.{ModifierId, ModifierTypeId}
import org.encryfoundation.generator.utils.Mnemonic.createPrivKey
import org.encryfoundation.generator.utils.{NetworkTimeProvider, Settings}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

class NetworkServer(settings: Settings,
                    timeProvider: NetworkTimeProvider,
                    influx: Option[ActorRef]) extends Actor with StrictLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.dispatcher

  var isConnected = false

  val messagesHandler: ActorRef = context.actorOf(NetworkMessagesHandler.props(settings))

  var tmpConnectionHandler: Option[ActorRef] = None

  val selfPeer: InetSocketAddress =
    new InetSocketAddress(settings.network.bindAddressHost, settings.network.bindAddressPort)

  val connectingPeer: InetSocketAddress =
    new InetSocketAddress(settings.network.peerForConnectionHost, settings.network.peerForConnectionPort)

  IO(Tcp) ! Bind(self, selfPeer, options = KeepAlive(true) :: Nil, pullMode = false)

  override def receive: Receive = {
    case Bound(localAddress) =>
      logger.info(s"Local app was successfully bound to $localAddress!")
      context.system.scheduler.schedule(5.seconds, 30.seconds, self, CheckConnection)

    case CommandFailed(_: Bind) =>
      logger.info(s"Failed to bind to $selfPeer.")
      context.stop(self)

    case Connected(remote, _) if !isConnected && remote.getAddress == connectingPeer.getAddress =>
      val handler: ActorRef = context.actorOf(
        PeerHandler.props(remote, sender(), settings, timeProvider, Outgoing, messagesHandler), "PeerHandler"
      )
      logger.info(s"Successfully connected to $remote. Creating handler: $handler.")
      isConnected = true
      tmpConnectionHandler = Some(handler)
      sender ! Register(handler)
      sender ! ResumeReading

    case Connected(remote, _) =>
      logger.info(s"Got Connected message but ${remote.getAddress} == ${connectingPeer.getAddress} " +
        s"${remote.getAddress == connectingPeer.getAddress} && $isConnected.")

    case CommandFailed(c: Connect) =>
      isConnected = false
      tmpConnectionHandler = None
      logger.info(s"Failed to connect to: ${c.remoteAddress}")

    case CheckConnection if !isConnected =>
      IO(Tcp) ! Connect(connectingPeer, options = KeepAlive(true) :: Nil, timeout = Some(5.seconds))
      logger.info(s"Trying to connect to $connectingPeer.")

    case CheckConnection =>
      logger.info(s"Triggered CheckConnection. Current connection is: $isConnected")

    case RemovePeerFromConnectionList(peer) =>
      isConnected = false
      tmpConnectionHandler = None
      logger.info(s"Disconnected from $peer.")

    case BroadcastInvForTx(tx) =>
      val inv: BasicMessagesRepo.NetworkMessage =
        InvNetworkMessage(ModifierTypeId @@ Transaction.modifierTypeId -> Seq(ModifierId @@ tx.id))
      tmpConnectionHandler.foreach(_ ! inv)
      logger.debug(s"Send inv message to remote.")

    case ConnectionSetupSuccessfully =>
      settings.peers.foreach { peer =>
        logger.info(s"Created generator actor for ${peer.explorerHost}:${peer.explorerPort}.")
        system.actorOf(
          Generator.props(settings, createPrivKey(Some(peer.mnemonicKey)), peer, influx, self), peer.explorerHost)
      }

    case msg@TransactionForCommit(_) => messagesHandler ! msg

    case msg => logger.info(s"Got strange message on NetworkServer: $msg.")
  }
}

object NetworkServer {

  case object CheckConnection

  case object ConnectionSetupSuccessfully

  def props(settings: Settings, timeProvider: NetworkTimeProvider, influx: Option[ActorRef]): Props =
    Props(new NetworkServer(settings, timeProvider, influx))
}