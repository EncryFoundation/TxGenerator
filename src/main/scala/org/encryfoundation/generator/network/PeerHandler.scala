package org.encryfoundation.generator.network

import java.net.InetSocketAddress
import NetworkMessagesProto.GeneralizedNetworkProtoMessage
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.io.Tcp._
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.network.PeerHandler._
import org.encryfoundation.generator.network.BasicMessagesRepo._
import org.encryfoundation.generator.utils.{NetworkTimeProvider, Settings}
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

class PeerHandler(remoteAddress: InetSocketAddress,
                  listener: ActorRef,
                  settings: Settings,
                  timeProvider: NetworkTimeProvider,
                  direction: ConnectionType,
                  messagesHandler: ActorRef) extends Actor with StrictLogging {

  context.watch(listener)

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def preStart(): Unit = context.parent ! AddPeerToConnectionList(remoteAddress)

  override def postStop(): Unit = {
    logger.info(s"Peer handler $self to $remoteAddress is destroyed.")
    listener ! Close
  }

  def awaitingConnectionBehaviour(isHandshakeSent: Boolean,
                                  receivedHandshake: Option[Handshake],
                                  timeout: Option[Cancellable]): Receive = {
    case AddedToConnectionListSuccessfully => timeProvider.time() map { time =>
      val handshake: Handshake = Handshake(
        protocolToBytes(settings.network.appVersion),
        settings.network.nodeName, None, time
      )
      listener ! Write(ByteString(GeneralizedNetworkMessage.toProto(handshake).toByteArray))
      logger.info(s"Sent initial handshake to $remoteAddress.")
      if (receivedHandshake.isDefined) self ! HandshakeDone
      else context.become(awaitingConnectionBehaviour(
        isHandshakeSent = true,
        None,
        Some(context.system.scheduler.scheduleOnce(settings.network.handshakeTimeout, self, HandshakeTimeout)))
      )
    }

    case HandshakeTimeout =>
      logger.info(s"Handshake timeout has expired for $remoteAddress, going to drop the connection.")
      self ! Close

    case HandshakeDone =>
      logger.info(s"Got successfully bounded connection with $remoteAddress. Starting working behaviour.")
      listener ! ResumeReading
      timeout.foreach(_.cancel())
      val peer: ConnectedPeer = ConnectedPeer(remoteAddress, self, direction, receivedHandshake.get)
      context.become(workingBehaviour(peer))

    case Received(data) => GeneralizedNetworkMessage.fromProto(data) match {
      case Success(value) => value match {
        case handshake: Handshake =>
          logger.info(s"Got a Handshake from $remoteAddress.")
          listener ! ResumeReading
          if (isHandshakeSent) self ! HandshakeDone
          else context.become(awaitingConnectionBehaviour(isHandshakeSent = false, Some(handshake), timeout))
        case message => logger.info(s"Expecting handshake, but received ${message.messageName}.")
      }
      case Failure(exception) =>
        logger.info(s"Error during parsing a handshake: $exception.")
        self ! Close
    }
    case _ =>
  }

  override def receive: Receive = awaitingConnectionBehaviour(isHandshakeSent = false, None, None)

  def workingBehaviour(cp: ConnectedPeer): Receive = defaultLogic
    .orElse(readDataFromRemote(cp))
    .orElse(writeDataToRemote)

  def defaultLogic: Receive = {
    case cc: ConnectionClosed =>
      logger.info(s"Connection closed to $remoteAddress cause ${cc.getErrorCause}.")
      context.stop(self)

    case fail@CommandFailed(cmd: Command) =>
      logger.info(s"Failed to execute command : $cmd cause ${fail.cause}.")
      listener ! ResumeReading
  }

  def readDataFromRemote(cp: ConnectedPeer): Receive = {
    case Received(data) => BasicMessagesRepo.GeneralizedNetworkMessage.fromProto(data) match {
      case Success(message) =>
        logger.info(s"Got new network message ${message.messageName} from $remoteAddress.")
        messagesHandler ! BasicMessagesRepo.MessageFromNetwork(message, Some(cp))
      case _ => logger.info(s"Can not parse received message!")
    }
      listener ! ResumeReading
  }

  def writeDataToRemote: Receive = {
    case message: NetworkMessage =>
      val serializedMessage: GeneralizedNetworkProtoMessage = BasicMessagesRepo.GeneralizedNetworkMessage.toProto(message)
      listener ! Write(ByteString(serializedMessage.toByteArray))
      logger.info(s"Sent ${message.messageName} to $remoteAddress")

    case _ => logger.info(s"Got something strange on PeerActor connected to $remoteAddress")
  }

  private def protocolToBytes(protocol: String): Array[Byte] = protocol.split("\\.").map(elem => elem.toByte)
}

object PeerHandler {

  sealed trait ConnectionMessages

  case object HandshakeTimeout extends ConnectionMessages

  case object HandshakeDone extends ConnectionMessages

  case object InnerPong extends ConnectionMessages

  case object AddedToConnectionListSuccessfully extends ConnectionMessages

  case class AddPeerToConnectionList(peer: InetSocketAddress) extends ConnectionMessages

  def props(remoteAddress: InetSocketAddress,
            listener: ActorRef,
            settings: Settings,
            timeProvider: NetworkTimeProvider,
            direction: ConnectionType,
            messagesHandler: ActorRef): Props =
    Props(new PeerHandler(remoteAddress, listener, settings, timeProvider, direction, messagesHandler))
}