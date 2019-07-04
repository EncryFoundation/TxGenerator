package org.encryfoundation.generator.modifiers.directives

import TransactionProto.TransactionProtoMessage.DirectiveProtoMessage
import TransactionProto.TransactionProtoMessage.DirectiveProtoMessage.{ADKeyProto, TransferDirectiveProtoMessage}
import com.google.common.primitives.{Bytes, Ints, Longs}
import com.google.protobuf.ByteString
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor}
import org.encryfoundation.common.modifiers.mempool.transaction.EncryAddress
import org.encryfoundation.common.modifiers.mempool.transaction.EncryAddress.Address
import org.encryfoundation.common.serialization.Serializer
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.common.utils.{Algos, Utils}
import org.encryfoundation.common.utils.constants.{Constants, TestNetConstants}
import scorex.crypto.hash.Digest32
import org.encryfoundation.generator.modifiers.box.{AssetBox, Box, EncryProposition}

import scala.util.Try

case class TransferDirective(address: Address,
                             amount: Long,
                             tokenIdOpt: Option[ADKey] = None) extends Directive {

  override type M                                          = TransferDirective

  override val typeId: Byte                                = TransferDirective.TypeId

  override def boxes(digest: Digest32, idx: Int): Seq[Box] =
    Seq(AssetBox(EncryProposition.addressLocked(address),
      Utils.nonceFromDigest(digest ++ Ints.toByteArray(idx)), amount, tokenIdOpt))

  override lazy val isValid: Boolean                       = amount > 0 && EncryAddress.resolveAddress(address).isSuccess

  override def serializer: Serializer[M]                   = TransferDirectiveSerializer

  lazy val isIntrinsic: Boolean                            = tokenIdOpt.isEmpty

  override def toDirectiveProto: DirectiveProtoMessage = TransferDirectiveProtoSerializer.toProto(this)

}

object TransferDirective {

  val TypeId: Byte = 1.toByte

  implicit val jsonEncoder: Encoder[TransferDirective] = (d: TransferDirective) => Map(
    "typeId"  -> d.typeId.asJson,
    "address" -> d.address.toString.asJson,
    "amount"  -> d.amount.asJson,
    "tokenId" -> d.tokenIdOpt.map(id => Algos.encode(id)).getOrElse("null").asJson
  ).asJson

  implicit val jsonDecoder: Decoder[TransferDirective] = (c: HCursor) => {
    for {
      address    <- c.downField("address").as[String]
      amount     <- c.downField("amount").as[Long]
      tokenIdOpt <- c.downField("tokenId").as[Option[String]]
    } yield {
      TransferDirective(
        address,
        amount,
        tokenIdOpt.flatMap(id => Algos.decode(id).map(ADKey @@ _).toOption)
      )
    }
  }
}

object TransferDirectiveProtoSerializer extends ProtoDirectiveSerializer[TransferDirective] {

  override def toProto(message: TransferDirective): DirectiveProtoMessage = {
    val initialDirective: TransferDirectiveProtoMessage = TransferDirectiveProtoMessage()
      .withAddress(message.address)
      .withAmount(message.amount)
    val transferDirective: TransferDirectiveProtoMessage = message.tokenIdOpt match {
      case Some(value) => initialDirective.withTokenIdOpt(ADKeyProto().withTokenIdOpt(ByteString.copyFrom(value)))
      case None => initialDirective
    }
    DirectiveProtoMessage().withTransferDirectiveProto(transferDirective)
  }

  override def fromProto(message: DirectiveProtoMessage): Option[TransferDirective] =
    message.directiveProto.transferDirectiveProto match {
      case Some(value) => Some(TransferDirective(
        value.address,
        value.amount,
        value.tokenIdOpt.map(x => ADKey @@ x.tokenIdOpt.toByteArray))
      )
      case None => Option.empty[TransferDirective]
    }
}

object TransferDirectiveSerializer extends Serializer[TransferDirective] {

  override def toBytes(obj: TransferDirective): Array[Byte] = {
    val address: Array[Byte] = obj.address.getBytes(Algos.charset)
    address.length.toByte +: Bytes.concat(
      address,
      Longs.toByteArray(obj.amount),
      obj.tokenIdOpt.getOrElse(Array.empty)
    )
  }

  override def parseBytes(bytes: Array[Byte]): Try[TransferDirective] = Try {
    val addressLen: Int           = bytes.head.toInt
    val address: Address          = new String(bytes.slice(1, 1 + addressLen), Algos.charset)
    val amount: Long              = Longs.fromByteArray(bytes.slice(1 + addressLen, 1 + addressLen + 8))
    val tokenIdOpt: Option[ADKey] = if ((bytes.length - (1 + addressLen + 8)) == TestNetConstants.ModifierIdSize) {
      Some(ADKey @@ bytes.takeRight(TestNetConstants.ModifierIdSize))
    } else None
    TransferDirective(address, amount, tokenIdOpt)
  }
}
