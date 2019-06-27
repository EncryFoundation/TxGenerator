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
import org.encryfoundation.generator.network.NetworkMessagesHandler.BroadcastInvForTx
import org.encryfoundation.generator.network.NetworkServer.RequestPeerForConnection
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
    new InetSocketAddress(settings.peer.peerHost, settings.peer.peerPort)

  var knownPeers: Map[InetSocketAddress, ActorRef] = Map.empty

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

    case any => logger.info(s"Got $any in bindLogic")
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
      knownPeers = knownPeers.updated(address, sender)
      system.actorOf(
        Generator.props(settings, createPrivKey(Some(settings.peer.mnemonicKey)), settings.peer, influx, self), settings.peer.explorerHost)
      context.become(businessLogic)

    case ConnectionRefused =>
      logger.info(s"Handshake timeout.. Starting awaiting process again...")
      context.system.scheduler.scheduleOnce(5.seconds, self, RequestPeerForConnection)

    case any => logger.info(s"Got $any in connectionWithPeerLogic")
  }

  def businessLogic: Receive = {
    case StopConnection(peer) =>
      knownPeers -= peer
      logger.info(s"Disconnected from $peer.")
      context.system.scheduler.scheduleOnce(5.seconds, self, RequestPeerForConnection)
      context.become(connectionWithPeerLogic)

    case BroadcastInvForTx(tx) =>
      val inv: NetworkMessage = InvNetworkMessage(ModifierTypeId @@ Transaction.modifierTypeId -> Seq(ModifierId @@ tx.id))
      knownPeers.foreach { case (add, ref) =>
        logger.debug(s"Send inv message to $add.")
        ref ! inv
      }
    case msg@TransactionForCommit(_) => messagesHandler ! msg
    case any => logger.info(s"Got $any in businessLogic")
  }
}

object NetworkServer {

  case object RequestPeerForConnection


  case object ConnectionSetupSuccessfully

  def props(settings: Settings, timeProvider: NetworkTimeProvider, influx: Option[ActorRef]): Props =
    Props(new NetworkServer(settings, timeProvider, influx))
}