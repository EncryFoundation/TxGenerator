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
                  receivedMessagesHandler: ActorRef) extends Actor with StrictLogging {

  context.watch(listener)

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def preStart(): Unit = self ! StartIteration

  override def postStop(): Unit = {
    logger.info(s"Peer handler $self to $remoteAddress is destroyed.")
    context.parent ! RemovePeerFromConnectionList(remoteAddress)
    listener ! Close
  }

  var receivedHandshake: Option[Handshake] = None
  var isHandshakeSent: Boolean = false
  var timeout: Option[Cancellable] = None

  def awaitingConnectionBehaviour: Receive = {
    case StartIteration => timeProvider.time() map { time =>
      val handshake: Handshake = Handshake(
        protocolToBytes(settings.network.appVersion),
        settings.network.nodeName, None, time
      )
      listener ! Write(ByteString(GeneralizedNetworkMessage.toProto(handshake).toByteArray))
      logger.info(s"Sent initial handshake to $remoteAddress.")
      isHandshakeSent = true
      timeout = Some(context.system.scheduler.scheduleOnce(settings.network.handshakeTimeout, self, HandshakeTimeout))
      if (receivedHandshake.isDefined && isHandshakeSent) self ! HandshakeDone
    }

    case HandshakeTimeout =>
      logger.info(s"Handshake timeout has expired for $remoteAddress, going to drop the connection.")
      self ! Close

    case HandshakeDone =>
      logger.info(s"Got successfully bounded connection with $remoteAddress. Starting working behaviour.")
      listener ! ResumeReading
      timeout.foreach(_.cancel())
      val peer: ConnectedPeer = ConnectedPeer(remoteAddress, self, Outgoing, receivedHandshake.get)
      context.become(workingBehaviour(peer))

    case Received(data) => GeneralizedNetworkMessage.fromProto(data) match {
      case Success(value) => value match {
        case handshake: Handshake =>
          logger.info(s"Got a Handshake from $remoteAddress.")
          listener ! ResumeReading
          receivedHandshake = Some(handshake)
          if (isHandshakeSent && receivedHandshake.isDefined) self ! HandshakeDone
        case message => logger.info(s"Expecting handshake, but received ${message.messageName}.")
      }
      case Failure(exception) =>
        logger.info(s"Error during parsing a handshake: $exception.")
        self ! Close
    }
    case _ =>
  }

  override def receive: Receive = awaitingConnectionBehaviour

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
        receivedMessagesHandler ! BasicMessagesRepo.MessageFromNetwork(message, Some(cp))
      case _ => logger.info(s"Can not parse received message!")
    }
      listener ! ResumeReading
  }

  def writeDataToRemote: Receive = {
    case message: NetworkMessage =>
      val serializedMessage: GeneralizedNetworkProtoMessage = BasicMessagesRepo.GeneralizedNetworkMessage.toProto(message)
      logger.info(s"Write $message to $remoteAddress")
      listener ! Write(ByteString(serializedMessage.toByteArray))

    case _ => logger.info(s"Got something strange on PeerActor connected to $remoteAddress")
  }

  private def protocolToBytes(protocol: String): Array[Byte] = protocol.split("\\.").map(elem => elem.toByte)
}

object PeerHandler {

  case object StartIteration

  sealed trait ConnectionMessages

  case object HandshakeTimeout extends ConnectionMessages

  case object HandshakeDone extends ConnectionMessages

  case class RemovePeerFromConnectionList(peer: InetSocketAddress) extends ConnectionMessages

  def props(remoteAddress: InetSocketAddress,
            listener: ActorRef,
            settings: Settings,
            timeProvider: NetworkTimeProvider,
            direction: ConnectionType,
            messagesHandler: ActorRef): Props =
    Props(new PeerHandler(remoteAddress, listener, settings, timeProvider, direction, messagesHandler))
}