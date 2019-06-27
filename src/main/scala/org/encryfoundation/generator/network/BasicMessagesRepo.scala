package org.encryfoundation.generator.network

import java.net.InetSocketAddress

import akka.actor.ActorRef
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.network.BasicMessagesRepo.{Handshake, NetworkMessage}
import org.encryfoundation.common.utils.TaggedTypes
import org.encryfoundation.common.utils.TaggedTypes.{ModifierId, ModifierTypeId}
import supertagged.@@

object BasicMessagesRepo extends StrictLogging {

  sealed trait ConnectionType
  case object Incoming extends ConnectionType
  case object Outgoing extends ConnectionType

  case class ConnectedPeer(socketAddress: InetSocketAddress,
                           handlerRef: ActorRef,
                           direction: ConnectionType,
                           handshake: Handshake) {

    override def toString: String = s"ConnectedPeer($socketAddress)"
  }

  final case class MessageFromNetwork(message: NetworkMessage, source: ConnectedPeer)

  case class SyncInfo(lastHeaderIds: Seq[ModifierId]) {

    def startingPoints: Seq[(Byte @@ TaggedTypes.ModifierTypeId.Tag, ModifierId)] =
      lastHeaderIds.map(id => ModifierTypeId @@ (101: Byte) -> id)

  }
}