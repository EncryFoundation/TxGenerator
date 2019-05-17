package org.encryfoundation.generator.network

import java.net.InetSocketAddress
import NetworkMessagesProto.GeneralizedNetworkProtoMessage
import NetworkMessagesProto.GeneralizedNetworkProtoMessage.InnerMessage
import NetworkMessagesProto.GeneralizedNetworkProtoMessage.InnerMessage.{HandshakeProtoMessage, InvProtoMessage, ModifiersProtoMessage, RequestModifiersProtoMessage}
import NetworkMessagesProto.GeneralizedNetworkProtoMessage.ModifiersProtoMessage.MapFieldEntry
import NetworkMessagesProto.GeneralizedNetworkProtoMessage.{HandshakeProtoMessage => hPM, InvProtoMessage => InvPM, ModifiersProtoMessage => ModifiersPM, RequestModifiersProtoMessage => rModsPM}
import SyntaxMessageProto.InetSocketAddressProtoMessage
import akka.actor.ActorRef
import com.google.protobuf.{ByteString => GoogleByteString}
import akka.util.{ByteString => AkkaByteString}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.network.BasicMessagesRepo.BasicMsgDataTypes.{InvData, ModifiersData}
import org.encryfoundation.generator.utils.CoreTaggedTypes.{ModifierId, ModifierTypeId}
import org.encryfoundation.generator.utils.Settings
import scorex.crypto.hash.Blake2b256
import scala.util.Try

object BasicMessagesRepo extends StrictLogging {

  object BasicMsgDataTypes {
    type InvData = (ModifierTypeId, Seq[ModifierId])
    type ModifiersData = (ModifierTypeId, Map[ModifierId, Array[Byte]])
  }

  sealed trait NetworkMessage {

    val messageName: String

    val NetworkMessageTypeID: Byte

    def checkSumBytes(innerMessage: InnerMessage): Array[Byte]

    def toInnerMessage: InnerMessage

    def isValid(setting: Settings): Boolean
  }

  sealed trait ProtoNetworkMessagesSerializer[T] {

    def toProto(message: T): InnerMessage

    def fromProto(message: InnerMessage): Option[T]
  }

  object MessageOptions {

    val MAGIC: GoogleByteString = GoogleByteString.copyFrom(Array[Byte](0x12: Byte, 0x34: Byte, 0x56: Byte, 0x78: Byte))

    val ChecksumLength: Int = 4

    def calculateCheckSum(bytes: Array[Byte]): GoogleByteString =
      GoogleByteString.copyFrom(Blake2b256.hash(bytes).take(ChecksumLength))
  }

  /**
    * @param message - message, received from network
    * @param source - sender of received message
    *
    *               This case class transfers network message from PeerConnectionHandler actor to the NetworkController.
    *               Main duty is to transfer message from network with sender of it message to the NetworkController as an end point.
    */

  case class MessageFromNetwork(message: NetworkMessage, source: Option[ConnectedPeer])

  /**
    * This object contains functions, connected with protobuf serialization to the generalized network message.
    *
    * toProto function first computes checkSum as a hash from NetworkMessageProtoSerialized bytes. Next,
    * assembles GeneralizedMessage, which contains from first dour calculated checkSum bytes, MAGIC constant, network message.
    *
    * fromProto function tries to serialize raw bytes to GeneralizedMessage and compare
    * magic bytes. Next, tries to collect networkMessage.
    */

  object GeneralizedNetworkMessage {

    def toProto(message: NetworkMessage): GeneralizedNetworkProtoMessage = {
      val innerMessage: InnerMessage = message.toInnerMessage
      val calculatedCheckSum: GoogleByteString = MessageOptions.calculateCheckSum(message.checkSumBytes(innerMessage))
      GeneralizedNetworkProtoMessage()
        .withMagic(MessageOptions.MAGIC)
        .withChecksum(calculatedCheckSum)
        .withInnerMessage(innerMessage)
    }

    def fromProto(message: AkkaByteString): Try[NetworkMessage] = Try {
      val netMessage: GeneralizedNetworkProtoMessage =
        GeneralizedNetworkProtoMessage.parseFrom(message.toArray)
      require(netMessage.magic.toByteArray.sameElements(MessageOptions.MAGIC.toByteArray),
        s"Wrong MAGIC! Got ${netMessage.magic.toByteArray.mkString(",")}")
      netMessage.innerMessage match {
        case InnerMessage.RequestModifiersProtoMessage(_) =>
          checkMessageValidity(RequestModifiersSerializer.fromProto, netMessage.innerMessage, netMessage.checksum)
        case InnerMessage.HandshakeProtoMessage(_) =>
          checkMessageValidity(HandshakeSerializer.fromProto, netMessage.innerMessage, netMessage.checksum)
        case InnerMessage.Empty => throw new RuntimeException("Empty inner message!")
        case _ => throw new RuntimeException("Can't find serializer for received message!")
      }
    }.flatten

    /**
      * @param serializer - function, which takes as a parameter other function, which provides serialisation to NetworkMessage.
      *                   As a result it gives serialized network message contained in option.
      * @param innerMessage - type of protobuf generalized nested message.
      * @param requiredBytes - checkSum, stored in received message.
      * @return - serialized network message contained in option.
      *
      *         This function provides validation check for inner message parsing and compares checkSum bytes.
      */

    def checkMessageValidity(serializer: InnerMessage => Option[NetworkMessage],
                             innerMessage: InnerMessage,
                             requiredBytes: GoogleByteString): Try[NetworkMessage] = Try {
      val serializedMessage: Option[NetworkMessage] = serializer(innerMessage)
      require(serializedMessage.isDefined, "Nested message is invalid!")
      val networkMessage: NetworkMessage = serializedMessage.get
      val checkSumBytes: Array[Byte] = networkMessage.checkSumBytes(innerMessage)
      val calculatedCheckSumBytes = MessageOptions.calculateCheckSum(checkSumBytes)
      require(calculatedCheckSumBytes.toByteArray.sameElements(requiredBytes.toByteArray),
        "Checksum of received message is invalid!")
      networkMessage
    }
  }

  /**
    * @param data - modifiersIds sequence.
    *
    *             This message sends as a respons for SyncInfoMessage or to show other peers locally generated modifier.
    */

  case class InvNetworkMessage(data: InvData) extends NetworkMessage {

    override val messageName: String = "Inv"

    override def checkSumBytes(innerMessage: InnerMessage): Array[Byte] =
      innerMessage.invProtoMessage.map(_.toByteArray).getOrElse(Array.emptyByteArray)

    override def toInnerMessage: InnerMessage = InvNetworkMessageSerializer.toProto(this)

    override val NetworkMessageTypeID: Byte = InvNetworkMessage.NetworkMessageTypeID

    override def isValid(setting: Settings): Boolean =
      if (data._2.size <= setting.network.syncPacketLength) true else false
  }

  object InvNetworkMessage {

    val NetworkMessageTypeID: Byte = 55: Byte
  }

  object InvNetworkMessageSerializer extends ProtoNetworkMessagesSerializer[InvNetworkMessage] {

    def toProto(message: InvNetworkMessage): InnerMessage = InvProtoMessage(InvPM()
      .withModifierTypeId(GoogleByteString.copyFrom(Array(message.data._1)))
      .withModifiers(message.data._2.map(elem => GoogleByteString.copyFrom(elem)))
    )

    def fromProto(message: InnerMessage): Option[InvNetworkMessage] = message.invProtoMessage match {
      case Some(value) => value.modifiers match {
        case mods: Seq[_] if mods.nonEmpty => Some(InvNetworkMessage(
          ModifierTypeId @@ value.modifierTypeId.toByteArray.head -> value.modifiers.map(x => ModifierId @@ x.toByteArray)))
        case _ => Option.empty[InvNetworkMessage]
      }
      case None => Option.empty[InvNetworkMessage]
    }
  }

  /**
    * @param data - modifiersIds sequence.
    *
    *             This message sends to the peer to request missing in local history modifiers.
    */

  case class RequestModifiersNetworkMessage(data: InvData) extends NetworkMessage {

    override val messageName: String = "RequestModifier"

    override def checkSumBytes(innerMessage: InnerMessage): Array[Byte] =
      innerMessage.requestModifiersProtoMessage.map(_.toByteArray).getOrElse(Array.emptyByteArray)

    override def toInnerMessage: InnerMessage = RequestModifiersSerializer.toProto(this)

    override val NetworkMessageTypeID: Byte = RequestModifiersNetworkMessage.NetworkMessageTypeID

    override def isValid(setting: Settings): Boolean =
      if (data._2.size <= setting.network.syncPacketLength) true else false
  }

  object RequestModifiersNetworkMessage {

    val NetworkMessageTypeID: Byte = 22: Byte
  }

  object RequestModifiersSerializer extends ProtoNetworkMessagesSerializer[RequestModifiersNetworkMessage] {

    override def toProto(message: RequestModifiersNetworkMessage): InnerMessage =
      RequestModifiersProtoMessage(
        rModsPM()
          .withModifierTypeId(GoogleByteString.copyFrom(Array(message.data._1)))
          .withModifiers(message.data._2.map(elem => GoogleByteString.copyFrom(elem)))
      )

    override def fromProto(message: InnerMessage): Option[RequestModifiersNetworkMessage] =
      message.requestModifiersProtoMessage match {
        case Some(value) => value.modifiers match {
          case mods: Seq[_] if mods.nonEmpty => Some(RequestModifiersNetworkMessage(
            ModifierTypeId @@ value.modifierTypeId.toByteArray.head -> value.modifiers.map(x => ModifierId @@ x.toByteArray)))
          case _ => Option.empty[RequestModifiersNetworkMessage]
        }
        case None => Option.empty[RequestModifiersNetworkMessage]
      }
  }

  /**
    * @param data - map with modifierId as a key and serialized to protobuf modifiers as a value.
    *
    *             This message sends as a RESPONSE ONLY to RequestModifiers message.
    */

  case class ModifiersNetworkMessage(data: ModifiersData) extends NetworkMessage {

    override val messageName: String = "Modifier"

    override def toInnerMessage: InnerMessage = ModifiersNetworkMessageSerializer.toProto(this)

    override def checkSumBytes(innerMessage: InnerMessage): Array[Byte] =
      innerMessage.modifiersProtoMessage.map(_.toByteArray).getOrElse(Array.emptyByteArray)

    override val NetworkMessageTypeID: Byte = ModifiersNetworkMessage.NetworkMessageTypeID

    override def isValid(setting: Settings): Boolean =
      if (data._2.size <= setting.network.syncPacketLength) true else false
  }

  object ModifiersNetworkMessage {

    val NetworkMessageTypeID: Byte = 33: Byte
  }

  object ModifiersNetworkMessageSerializer extends ProtoNetworkMessagesSerializer[ModifiersNetworkMessage] {

    override def toProto(message: ModifiersNetworkMessage): InnerMessage = ModifiersProtoMessage(ModifiersPM()
      .withModifierTypeId(GoogleByteString.copyFrom(Array(message.data._1)))
      .withMap(message.data._2.map(element =>
        MapFieldEntry().withKey(GoogleByteString.copyFrom(element._1)).withValue(GoogleByteString.copyFrom(element._2))).toSeq))

    override def fromProto(message: InnerMessage): Option[ModifiersNetworkMessage] = message.modifiersProtoMessage match {
      case Some(value) => Some(ModifiersNetworkMessage(ModifierTypeId @@ value.modifierTypeId.toByteArray.head ->
        value.map.map(element => ModifierId @@ element.key.toByteArray -> element.value.toByteArray).toMap))
      case None => Option.empty[ModifiersNetworkMessage]
    }
  }

  /**
    * @param protocolVersion - peer network communication protocol version
    * @param nodeName        - peer name
    * @param declaredAddress - peer address
    * @param time            - handshake creation time
    *
    *                        This network message are using for set up network connection between two peers.
    *                        First peer sends this message to the second one. Second peer processes this message
    *                        and send back response with it's own handshake. After both
    *                        peers received handshakes from each other, network connection raises.
    */

  case class Handshake(protocolVersion: Array[Byte],
                       nodeName: String,
                       declaredAddress: Option[InetSocketAddress],
                       time: Long) extends NetworkMessage {

    require(protocolVersion.length > 0, "Empty protocol version!")

    override val messageName: String = "Handshake"

    override def toInnerMessage: InnerMessage = HandshakeSerializer.toProto(this)

    override def checkSumBytes(innerMessage: InnerMessage): Array[Byte] =
      innerMessage.handshakeProtoMessage.map(_.toByteArray).getOrElse(Array.emptyByteArray)

    override val NetworkMessageTypeID: Byte = Handshake.NetworkMessageTypeID

    override def isValid(setting: Settings): Boolean = true
  }

  object Handshake {

    val NetworkMessageTypeID: Byte = 75: Byte
  }

  object HandshakeSerializer extends ProtoNetworkMessagesSerializer[Handshake] {

    override def toProto(message: Handshake): InnerMessage = {
      val initialHandshakeProto: hPM = hPM()
        .withProtocolVersion(GoogleByteString.copyFrom(message.protocolVersion))
        .withNodeName(message.nodeName)
        .withTime(message.time)
      val updatedHandshakeProto: hPM = message.declaredAddress match {
        case Some(value) => initialHandshakeProto.withDeclaredAddress(InetSocketAddressProtoMessage()
          .withHost(value.getHostName)
          .withPort(value.getPort)
        )
        case None => initialHandshakeProto
      }
      HandshakeProtoMessage(updatedHandshakeProto)
    }

    override def fromProto(message: InnerMessage): Option[Handshake] = message.handshakeProtoMessage match {
      case Some(value) => value.nodeName match {
        case name: String if name.nonEmpty => Some(
          Handshake(
            value.protocolVersion.toByteArray,
            value.nodeName,
            value.declaredAddress.map(element => new InetSocketAddress(element.host, element.port)),
            value.time
          ))
        case _ => Option.empty[Handshake]
      }
      case None => Option.empty[Handshake]
    }
  }

  sealed trait ConnectionType
  case object Incoming extends ConnectionType
  case object Outgoing extends ConnectionType

  case class ConnectedPeer(socketAddress: InetSocketAddress,
                           handlerRef: ActorRef,
                           direction: ConnectionType,
                           handshake: Handshake) {

    def publicPeer: Boolean = handshake.declaredAddress.contains(socketAddress)

    override def hashCode(): Int = socketAddress.hashCode()

    override def toString: String = s"ConnectedPeer($socketAddress)"
  }
}