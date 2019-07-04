package org.encryfoundation.generator.modifiers.directives

import TransactionProto.TransactionProtoMessage.DirectiveProtoMessage
import TransactionProto.TransactionProtoMessage.DirectiveProtoMessage.{ADKeyProto, ScriptedAssetDirectiveProtoMessage}

import scala.util.Try
import com.google.common.primitives.{Bytes, Ints, Longs}
import com.google.protobuf.ByteString
import org.encryfoundation.common.serialization.Serializer
import org.encryfoundation.common.utils.{Algos, Utils}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor}
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.common.utils.constants.TestNetConstants
import org.encryfoundation.prismlang.compiler.CompiledContract.ContractHash
import scorex.crypto.hash.Digest32
import org.encryfoundation.generator.modifiers.box.{AssetBox, Box, EncryProposition}

case class ScriptedAssetDirective(contractHash: ContractHash,
                                  amount: Long,
                                  tokenIdOpt: Option[ADKey] = None) extends Directive {

  override type M                        = ScriptedAssetDirective

  override val typeId: Byte              = ScriptedAssetDirective.TypeId

  override def boxes(digest: Digest32, idx: Int): Seq[Box] =
    Seq(AssetBox(EncryProposition(contractHash),
      Utils.nonceFromDigest(digest ++ Ints.toByteArray(idx)), amount))

  override lazy val isValid: Boolean     = amount > 0

  override def serializer: Serializer[M] = ScriptedAssetDirectiveSerializer

  lazy val isIntrinsic: Boolean          = tokenIdOpt.isEmpty

  override def toDirectiveProto: DirectiveProtoMessage = ScriptedAssetDirectiveProtoSerializer.toProto(this)

}

object ScriptedAssetDirective {

  val TypeId: Byte = 3.toByte

  implicit val jsonEncoder: Encoder[ScriptedAssetDirective] = (d: ScriptedAssetDirective) => Map(
    "typeId" -> d.typeId.asJson,
    "contractHash" -> Algos.encode(d.contractHash).asJson,
    "amount" -> d.amount.asJson,
    "tokenId" -> d.tokenIdOpt.map(id => Algos.encode(id)).asJson
  ).asJson

  implicit val jsonDecoder: Decoder[ScriptedAssetDirective] = (c: HCursor) => for {
    contractHash <- c.downField("contractHash").as[ContractHash](Decoder.decodeString.emapTry(Algos.decode))
    amount <- c.downField("amount").as[Long]
    tokenIdOpt <- c.downField("tokenId").as[Option[ADKey]](Decoder.decodeOption(Decoder.decodeString.emapTry(Algos.decode).map(ADKey @@ _)))
  } yield ScriptedAssetDirective(contractHash, amount, tokenIdOpt)
}

object ScriptedAssetDirectiveProtoSerializer extends ProtoDirectiveSerializer[ScriptedAssetDirective] {

  override def toProto(message: ScriptedAssetDirective): DirectiveProtoMessage ={
    val initialDirective: ScriptedAssetDirectiveProtoMessage = ScriptedAssetDirectiveProtoMessage()
      .withContractHash(ByteString.copyFrom(message.contractHash))
      .withAmount(message.amount)
    val saDirective: ScriptedAssetDirectiveProtoMessage = message.tokenIdOpt match {
      case Some(value) => initialDirective.withTokenIdOpt( ADKeyProto().withTokenIdOpt(ByteString.copyFrom(value)))
      case None => initialDirective
    }
    DirectiveProtoMessage().withScriptedAssetDirectiveProto(saDirective)
  }

  override def fromProto(message: DirectiveProtoMessage): Option[ScriptedAssetDirective] =
    message.directiveProto.scriptedAssetDirectiveProto match {
      case Some(value) => Some(ScriptedAssetDirective(
        value.contractHash.toByteArray,
        value.amount,
        value.tokenIdOpt.map(x => ADKey @@ x.tokenIdOpt.toByteArray))
      )
      case None => Option.empty[ScriptedAssetDirective]
    }
}

object ScriptedAssetDirectiveSerializer extends Serializer[ScriptedAssetDirective] {

  override def toBytes(obj: ScriptedAssetDirective): Array[Byte] =
    Bytes.concat(
      obj.contractHash,
      Longs.toByteArray(obj.amount),
      obj.tokenIdOpt.getOrElse(Array.empty)
    )

  override def parseBytes(bytes: Array[Byte]): Try[ScriptedAssetDirective] = Try {
    val contractHash: ContractHash = bytes.take(TestNetConstants.DigestLength)
    val amount: Long = Longs.fromByteArray(bytes.slice(TestNetConstants.DigestLength, TestNetConstants.DigestLength + 8))
    val tokenIdOpt: Option[ADKey] = if ((bytes.length - (TestNetConstants.DigestLength + 8)) == TestNetConstants.ModifierIdSize) {
      Some(ADKey @@ bytes.takeRight(TestNetConstants.ModifierIdSize))
    } else None
    ScriptedAssetDirective(contractHash, amount, tokenIdOpt)
  }
}
