package org.encryfoundation.generator.network

import java.net.InetSocketAddress
import java.nio.ByteOrder
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.{ByteString, CompactByteString}
import com.google.common.primitives.Ints
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.network.BasicMessagesRepo.{GeneralizedNetworkMessage, Handshake, NetworkMessage}
import org.encryfoundation.generator.network.PeerHandler._
import org.encryfoundation.generator.network.BasicMessagesRepo._
import org.encryfoundation.generator.utils.{NetworkTimeProvider, Settings}
import scala.annotation.tailrec
import scala.collection.immutable.HashMap
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

class PeerHandler(remoteAddress: InetSocketAddress,
                  selfAddress: InetSocketAddress,
                  remote: ActorRef,
                  settings: Settings,
                  timeProvider: NetworkTimeProvider,
                  receivedMessagesHandler: ActorRef) extends Actor with StrictLogging {

  context.watch(remote)

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def preStart(): Unit = self ! SendInitialHandshake

  override def postStop(): Unit = {
    logger.info(s"Peer handler $self to $remoteAddress is destroyed.")
    remote ! Close
  }

  var chunksBuffer: ByteString = CompactByteString.empty
  var outMessagesBuffer: HashMap[Long, ByteString] = HashMap.empty
  var outMessagesCounter: Long = 0

  def handshakeSendingLogic(isHandshakeSent: Boolean,
                            receivedHandshake: Option[Handshake],
                            timeout: Option[Cancellable]): Receive = {
    case SendInitialHandshake => timeProvider.time().map { time =>
      val handshake: Handshake = Handshake(
        protocolToBytes(settings.network.appVersion), settings.network.nodeName, Some(selfAddress), time
      )
      remote ! Write(ByteString(GeneralizedNetworkMessage.toProto(handshake).toByteArray))
      logger.info(s"Sent initial handshake to $remoteAddress.")
      if (receivedHandshake.isDefined) {
        logger.info(s"Successfully done handshake process.")
        timeout.foreach(_.cancel())
        context.parent ! HandshakeDone(receivedHandshake.get.declaredAddress.getOrElse(remoteAddress))
        context.become(workingCycleWriting(ConnectedPeer(remoteAddress, self, Outgoing, receivedHandshake.get)))
      } else context.become(handshakeSendingLogic(
        isHandshakeSent = true,
        receivedHandshake,
        Some(context.system.scheduler.scheduleOnce(settings.network.handshakeTimeout, self, HandshakeTimeout)))
      )
    }
    case Received(data) => GeneralizedNetworkMessage.fromProto(data.toArray) match {
      case Success(value) => value match {
        case handshake: Handshake =>
          logger.info(s"Got a Handshake from $remoteAddress.")
          if (isHandshakeSent) {
            logger.info(s"Successfully done handshake process.")
            timeout.foreach(_.cancel())
            context.parent ! HandshakeDone(receivedHandshake.get.declaredAddress.getOrElse(remoteAddress))
            context.become(workingCycleWriting(ConnectedPeer(remoteAddress, self, Outgoing, receivedHandshake.get)))
          } else context.become(handshakeSendingLogic(
            isHandshakeSent,
            receivedHandshake = Some(handshake),
            Some(context.system.scheduler.scheduleOnce(settings.network.handshakeTimeout, self, HandshakeTimeout)))
          )
        case message => logger.info(s"Expecting handshake, but received ${message.messageName}.")
      }

      case Failure(exception) =>
        logger.info(s"Error during parsing a handshake: $exception.")
        context.parent ! ConnectionRefused
        self ! Close
    }
    case HandshakeTimeout =>
      if (isHandshakeSent && receivedHandshake.isDefined) {
        logger.info(s"Connection successfully bounded.")
        context.parent ! HandshakeDone(receivedHandshake.get.declaredAddress.getOrElse(remoteAddress))
        context.become(workingCycleWriting(ConnectedPeer(remoteAddress, self, Outgoing, receivedHandshake.get)))
      } else {
        logger.info(s"Handshake timeout has expired for $remoteAddress, going to drop the connection.")
        context.parent ! ConnectionRefused
        self ! Close
      }
  }

  override def receive: Receive = handshakeSendingLogic(isHandshakeSent = false, None, None)

  def workingCycleWriting(cp: ConnectedPeer): Receive = workingCycleLocalInterfaceWritingMode(cp)
    .orElse(workingCycleRemoteInterface(cp))
    .orElse(errorsHandler(cp))

  def workingCycleLocalInterfaceWritingMode(cp: ConnectedPeer): Receive = {
    case message: NetworkMessage =>
      def sendMessage(): Unit = {
        outMessagesCounter += 1
        logger.debug(s"Sent to $remote msg: ${message.messageName}")
        val messageToNetwork: Array[Byte] = GeneralizedNetworkMessage.toProto(message).toByteArray
        val bytes: ByteString = ByteString(Ints.toByteArray(messageToNetwork.length) ++ messageToNetwork)
        remote ! Write(bytes, Ack(outMessagesCounter))
      }
      sendMessage()

    case fail@CommandFailed(Write(msg, Ack(id))) =>
      logger.debug(s"Failed to write ${msg.length} bytes to $remote cause ${fail.cause}, switching to buffering mode")
      remote ! ResumeReading
      toBuffer(id, msg)
      context.become(workingCycleBuffering(cp))

    case Ack(_) => // ignore ACKs in stable mode
    case WritingResumed => // ignore in stable mode
  }

  def workingCycleRemoteInterface(cp: ConnectedPeer): Receive = {
    case Received(data) =>
      val packet: (List[ByteString], ByteString) = getPacket(chunksBuffer ++ data)
      chunksBuffer = packet._2
      packet._1.find { packet =>
        GeneralizedNetworkMessage.fromProto(packet.toArray) match {
          case Success(message) =>
            receivedMessagesHandler ! MessageFromNetwork(message, cp)
            logger.info("Received message " + message.messageName + " from " + remoteAddress)
            false
          case Failure(e) =>
            logger.info(s"Corrupted data from: " + remoteAddress + s"$e")
            true
        }
      }
      remote ! ResumeReading
  }

  def workingCycleBuffering(cp: ConnectedPeer): Receive = workingCycleLocalInterfaceBufferingMode(cp)
    .orElse(workingCycleRemoteInterface(cp))
    .orElse(errorsHandler(cp))

  def errorsHandler(connectedPeer: ConnectedPeer): Receive = {
    case cc: ConnectionClosed =>
      logger.info(s"Connection closed to $remoteAddress cause ${cc.getErrorCause}.")
      context.parent ! StopConnection(connectedPeer.socketAddress)
      context.stop(self)

    case fail@CommandFailed(cmd: Command) =>
      logger.info(s"Failed to execute command : $cmd cause ${fail.cause}.")
      remote ! ResumeReading

    case _ =>
  }

  // operate in ACK mode until all buffered messages are transmitted
  def workingCycleLocalInterfaceBufferingMode(cp: ConnectedPeer): Receive = {
    case message: NetworkMessage =>
      outMessagesCounter += 1
      val messageToNetwork: Array[Byte] = GeneralizedNetworkMessage.toProto(message).toByteArray
      val bytes: ByteString = ByteString(Ints.toByteArray(messageToNetwork.length) ++ messageToNetwork)
      toBuffer(outMessagesCounter, bytes)
    case fail@CommandFailed(Write(msg, Ack(id))) =>
      logger.debug(s"Failed to buffer ${msg.length} bytes to $remoteAddress cause ${fail.cause}")
      remote ! ResumeWriting
      toBuffer(id, msg)
    case CommandFailed(ResumeWriting) => // ignore in ACK mode
    case WritingResumed => writeFirst()
    case Ack(id) =>
      outMessagesBuffer -= id
      if (outMessagesBuffer.nonEmpty) writeFirst()
      else {
        logger.info("Buffered messages processed, exiting buffering mode")
        context.become(workingCycleWriting(cp))
      }
  }

  def getPacket(data: ByteString): (List[ByteString], ByteString) = {

    val headerSize: Int = 4

    @tailrec
    def multiPacket(packets: List[ByteString], current: ByteString): (List[ByteString], ByteString) =
      if (current.length < headerSize) (packets.reverse, current)
      else {
        val len: Int = current.iterator.getInt(ByteOrder.BIG_ENDIAN)
        if (current.length < len + headerSize) (packets.reverse, current)
        else {
          val rem: ByteString = current drop headerSize
          val (front: ByteString, back: ByteString) = rem.splitAt(len)
          multiPacket(front :: packets, back)
        }
      }

    multiPacket(List[ByteString](), data)
  }

  def writeFirst(): Unit = outMessagesBuffer.headOption.foreach { case (id, msg) => remote ! Write(msg, Ack(id)) }

  def writeAll(): Unit = outMessagesBuffer.foreach { case (id, msg) => remote ! Write(msg, Ack(id)) }

  def toBuffer(id: Long, message: ByteString): Unit = outMessagesBuffer += id -> message

  private def protocolToBytes(protocol: String): Array[Byte] = protocol.split("\\.").map(elem => elem.toByte)
}

object PeerHandler {

  case object SendInitialHandshake

  final case class HandshakeDone(declaredAddress: InetSocketAddress) extends AnyVal

  case object ConnectionRefused

  case object HandshakeTimeout

  final case class StopConnection(peer: InetSocketAddress) extends AnyVal

  case class RemovePeerFromConnectionList(peer: InetSocketAddress)

  final case class Ack(offset: Long) extends Tcp.Event

  def props(remoteAddress: InetSocketAddress,
            selfAddress: InetSocketAddress,
            listener: ActorRef,
            settings: Settings,
            timeProvider: NetworkTimeProvider,
            messagesHandler: ActorRef): Props =
    Props(new PeerHandler(remoteAddress, selfAddress, listener, settings, timeProvider, messagesHandler))
}