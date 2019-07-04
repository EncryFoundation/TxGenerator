package org.encryfoundation.generator.modifiers.directives

import TransactionProto.TransactionProtoMessage.DirectiveProtoMessage
import TransactionProto.TransactionProtoMessage.DirectiveProtoMessage.AssetIssuingDirectiveProtoMessage
import com.google.common.primitives.{Bytes, Ints, Longs}
import com.google.protobuf.ByteString
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor}
import org.encryfoundation.common.serialization.Serializer
import org.encryfoundation.common.utils.{Algos, Utils}
import org.encryfoundation.common.utils.constants.TestNetConstants
import org.encryfoundation.prismlang.compiler.CompiledContract.ContractHash
import scorex.crypto.hash.Digest32
import org.encryfoundation.generator.modifiers.box.{Box, EncryProposition, TokenIssuingBox}

import scala.util.Try

case class AssetIssuingDirective(contractHash: ContractHash, amount: Long) extends Directive {

  override type M                    = AssetIssuingDirective
  override val typeId: Byte          = AssetIssuingDirective.TypeId
  override lazy val isValid: Boolean = amount > 0

  override def boxes(digest: Digest32, idx: Int): Seq[Box] =
    Seq(TokenIssuingBox(
      EncryProposition(contractHash),
      Utils.nonceFromDigest(digest ++ Ints.toByteArray(idx)),
      amount,
      Algos.hash(Ints.toByteArray(idx) ++ digest)
    ))

  override def serializer: Serializer[M] = AssetIssuingDirectiveSerializer

  override def toDirectiveProto: DirectiveProtoMessage = AssetIssuingDirectiveProtoSerializer.toProto(this)

}

object AssetIssuingDirective {

  val TypeId: Byte = 2.toByte

  implicit val jsonEncoder: Encoder[AssetIssuingDirective] = (d: AssetIssuingDirective) => Map(
    "typeId"       -> d.typeId.asJson,
    "contractHash" -> Algos.encode(d.contractHash).asJson,
    "amount"       -> d.amount.asJson
  ).asJson

  implicit val jsonDecoder: Decoder[AssetIssuingDirective] = (c: HCursor) => {
    for {
      contractHash <- c.downField("contractHash").as[ContractHash](Decoder.decodeString.emapTry(Algos.decode))
      amount       <- c.downField("amount").as[Long]
    } yield AssetIssuingDirective(contractHash, amount)
  }
}

object AssetIssuingDirectiveProtoSerializer extends ProtoDirectiveSerializer[AssetIssuingDirective] {

  override def toProto(message: AssetIssuingDirective): DirectiveProtoMessage =
    DirectiveProtoMessage().withAssetIssuingDirectiveProto(AssetIssuingDirectiveProtoMessage()
      .withAmount(message.amount)
      .withContractHash(ByteString.copyFrom(message.contractHash))
    )

  override def fromProto(message: DirectiveProtoMessage): Option[AssetIssuingDirective] =
    message.directiveProto.assetIssuingDirectiveProto match {
      case Some(value) => Some(AssetIssuingDirective(value.contractHash.toByteArray, value.amount))
      case None => Option.empty[AssetIssuingDirective]
    }
}


object AssetIssuingDirectiveSerializer extends Serializer[AssetIssuingDirective] {

  override def toBytes(obj: AssetIssuingDirective): Array[Byte] =
    Bytes.concat(
      obj.contractHash,
      Longs.toByteArray(obj.amount)
    )

  override def parseBytes(bytes: Array[Byte]): Try[AssetIssuingDirective] = Try {
    val contractHash: ContractHash = bytes.take(TestNetConstants.DigestLength)
    val amount: Long               = Longs.fromByteArray(bytes.slice(TestNetConstants.DigestLength, TestNetConstants.DigestLength + 8))
    AssetIssuingDirective(contractHash, amount)
  }
}
