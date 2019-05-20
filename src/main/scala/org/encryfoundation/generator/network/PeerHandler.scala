package org.encryfoundation.generator.network

import java.net.InetSocketAddress
import java.nio.ByteOrder
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.io.Tcp
import akka.io.Tcp._
import akka.util.{ByteString, CompactByteString}
import com.google.common.primitives.Ints
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.network.PeerHandler._
import org.encryfoundation.generator.network.BasicMessagesRepo._
import org.encryfoundation.generator.utils.{NetworkTimeProvider, Settings}
import scala.annotation.tailrec
import scala.collection.immutable.HashMap
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

  var selfPeer: Option[ConnectedPeer] = None
  var chunksBuffer: ByteString = CompactByteString.empty
  var receivedHandshake: Option[Handshake] = None
  var isHandshakeSent: Boolean = false
  var timeout: Option[Cancellable] = None
  var outMessagesBuffer: HashMap[Long, ByteString] = HashMap.empty
  var outMessagesCounter: Long = 0

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
      selfPeer = Some(ConnectedPeer(remoteAddress, self, Outgoing, receivedHandshake.get))
      context.become(workingCycleWriting)

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

  def defaultLogic: Receive = {
    case cc: ConnectionClosed =>
      logger.info(s"Connection closed to $remoteAddress cause ${cc.getErrorCause}.")
      context.stop(self)

    case fail@CommandFailed(cmd: Command) =>
      logger.info(s"Failed to execute command : $cmd cause ${fail.cause}.")
      listener ! ResumeReading

    case _ =>
  }

  def workingCycleWriting: Receive = workingCycleLocalInterfaceWritingMode
    .orElse(workingCycleRemoteInterface)
    .orElse(defaultLogic)

  def workingCycleLocalInterfaceWritingMode: Receive = {
    case message: NetworkMessage =>
      def sendMessage(): Unit = {
        outMessagesCounter += 1
        val messageToNetwork: Array[Byte] = GeneralizedNetworkMessage.toProto(message).toByteArray
        val bytes: ByteString = ByteString(Ints.toByteArray(messageToNetwork.length) ++ messageToNetwork)
        listener ! Write(bytes, Ack(outMessagesCounter))
      }

      sendMessage()

    case fail@CommandFailed(Write(msg, Ack(id))) =>
      logger.debug(s"Failed to write ${msg.length} bytes to $remoteAddress cause ${fail.cause}, switching to buffering mode")
      listener ! ResumeReading
      toBuffer(id, msg)
      context.become(workingCycleBuffering)
    case Ack(_) => // ignore ACKs in stable mode
    case WritingResumed => // ignore in stable mode
  }

  def workingCycleRemoteInterface: Receive = {
    case Received(data) =>
      val packet: (List[ByteString], ByteString) = getPacket(chunksBuffer ++ data)
      chunksBuffer = packet._2
      packet._1.find { packet =>
        GeneralizedNetworkMessage.fromProto(packet) match {
          case Success(message) =>
            receivedMessagesHandler ! MessageFromNetwork(message, selfPeer)
            logger.info("Received message " + message.messageName + " from " + remoteAddress)
            false
          case Failure(e) =>
            logger.info(s"Corrupted data from: " + remoteAddress + s"$e")
            true
        }
      }
      listener ! ResumeReading
  }

  def workingCycleBuffering: Receive = workingCycleLocalInterfaceBufferingMode
    .orElse(workingCycleRemoteInterface)
    .orElse(defaultLogic)

  // operate in ACK mode until all buffered messages are transmitted
  def workingCycleLocalInterfaceBufferingMode: Receive = {
    case message: NetworkMessage =>
      outMessagesCounter += 1
      val messageToNetwork: Array[Byte] = GeneralizedNetworkMessage.toProto(message).toByteArray
      val bytes: ByteString = ByteString(Ints.toByteArray(messageToNetwork.length) ++ messageToNetwork)
      toBuffer(outMessagesCounter, bytes)
    case fail@CommandFailed(Write(msg, Ack(id))) =>
      logger.debug(s"Failed to buffer ${msg.length} bytes to $remoteAddress cause ${fail.cause}")
      listener ! ResumeWriting
      toBuffer(id, msg)
    case CommandFailed(ResumeWriting) => // ignore in ACK mode
    case WritingResumed => writeFirst()
    case Ack(id) =>
      outMessagesBuffer -= id
      if (outMessagesBuffer.nonEmpty) writeFirst()
      else {
        logger.info("Buffered messages processed, exiting buffering mode")
        context become workingCycleWriting
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

  def writeFirst(): Unit = outMessagesBuffer.headOption.foreach { case (id, msg) => listener ! Write(msg, Ack(id)) }

  def writeAll(): Unit = outMessagesBuffer.foreach { case (id, msg) => listener ! Write(msg, Ack(id)) }

  def toBuffer(id: Long, message: ByteString): Unit = outMessagesBuffer += id -> message

  private def protocolToBytes(protocol: String): Array[Byte] = protocol.split("\\.").map(elem => elem.toByte)
}

object PeerHandler {

  case object StartIteration

  sealed trait ConnectionMessages

  case object HandshakeTimeout extends ConnectionMessages

  case object HandshakeDone extends ConnectionMessages

  case class RemovePeerFromConnectionList(peer: InetSocketAddress) extends ConnectionMessages

  final case class Ack(offset: Long) extends Tcp.Event

  def props(remoteAddress: InetSocketAddress,
            listener: ActorRef,
            settings: Settings,
            timeProvider: NetworkTimeProvider,
            direction: ConnectionType,
            messagesHandler: ActorRef): Props =
    Props(new PeerHandler(remoteAddress, listener, settings, timeProvider, direction, messagesHandler))
}