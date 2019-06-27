package org.encryfoundation.generator.network

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, ActorSystem, OneForOneStrategy, Props, SupervisorStrategy}
import akka.io.Tcp.SO.KeepAlive
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.network.BasicMessagesRepo.{InvNetworkMessage, NetworkMessage}
import org.encryfoundation.common.utils.TaggedTypes.{ModifierId, ModifierTypeId}
import org.encryfoundation.generator.actors.Generator
import org.encryfoundation.generator.actors.Generator.TransactionForCommit
import org.encryfoundation.generator.network.BasicMessagesRepo.Outgoing
import org.encryfoundation.generator.network.NetworkMessagesHandler.BroadcastInvForTx
import org.encryfoundation.generator.network.NetworkServer.{ConnectionSetupSuccessfully, RequestPeerForConnection}
import org.encryfoundation.generator.network.PeerHandler._
import org.encryfoundation.generator.modifiers.Transaction
import org.encryfoundation.generator.utils.Mnemonic.createPrivKey
import org.encryfoundation.generator.utils.{NetworkTimeProvider, Settings}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

class NetworkServer(settings: Settings,
                    timeProvider: NetworkTimeProvider,
                    influx: Option[ActorRef]) extends Actor with StrictLogging {

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = context.dispatcher

  val messagesHandler: ActorRef = context.actorOf(NetworkMessagesHandler.props(settings))

  val selfPeer: InetSocketAddress =
    new InetSocketAddress(settings.network.bindAddressHost, settings.network.bindAddressPort)

  val connectingPeer: InetSocketAddress =
    new InetSocketAddress(settings.network.peerForConnectionHost, settings.network.peerForConnectionPort)

  IO(Tcp) ! Bind(self, selfPeer, options = KeepAlive(true) :: Nil, pullMode = false)

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(
    maxNrOfRetries = 5, withinTimeRange = 60.seconds
  ) { case _ => Restart }

  override def receive: Receive = bindLogic

  def bindLogic: Receive = {
    case Bound(address) =>
      logger.info(s"Local app was successfully bound to $address!")
      context.system.scheduler.scheduleOnce(5.seconds, self, RequestPeerForConnection)
      context.become(connectionWithPeerLogic)

    case CommandFailed(add: Bind) =>
      logger.info(s"Failed to bind to ${add.localAddress} cause of: ${add.failureMessage.cause}. Stopping network actor.")
      context.stop(self)
  }

  def connectionWithPeerLogic: Receive = {
    case RequestPeerForConnection =>
      logger.info(s"Sending connect message to $connectingPeer.")
      IO(Tcp) ! Connect(
        connectingPeer,
        options = KeepAlive(true) :: Nil,
        timeout = Some(5.seconds)
      )

    case Connected(remote, _) if remote.getAddress == connectingPeer.getAddress =>
      logger.info(s"Got Connected message from $remote. Trying to send handshake message.")
      context.actorOf(PeerHandler.props(remote, selfPeer, sender(), settings, timeProvider, messagesHandler))

    case CommandFailed(c: Connect) =>
      logger.info(s"Failed to connect to: ${c.remoteAddress}")
      context.system.scheduler.scheduleOnce(5.seconds, self, RequestPeerForConnection)

    case HandshakeDone(address) =>
      logger.info(s"Handshake process done. Starting business logic..")
      context.become(businessLogic)

    case ConnectionRefused =>
      logger.info(s"Handshake timeout.. Starting awaiting process again...")
      context.system.scheduler.scheduleOnce(5.seconds, self, RequestPeerForConnection)
  }

  def businessLogic: Receive = {

    case RemovePeerFromConnectionList(peer) =>
      isConnected = false
      tmpConnectionHandler = None
      logger.info(s"Disconnected from $peer.")

    case BroadcastInvForTx(tx) =>
      val inv: NetworkMessage =
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

  def errorHandler: Receive = {
    case _ =>
  }
}

object NetworkServer {

  case object RequestPeerForConnection


  case object ConnectionSetupSuccessfully

  def props(settings: Settings, timeProvider: NetworkTimeProvider, influx: Option[ActorRef]): Props =
    Props(new NetworkServer(settings, timeProvider, influx))
}