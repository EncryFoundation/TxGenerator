package org.encryfoundation.generator.transaction.box

import com.google.common.primitives.{Bytes, Longs, Shorts}
import io.circe.Encoder
import io.circe.syntax._
import org.encryfoundation.common.Algos
import org.encryfoundation.common.serialization.Serializer
import org.encryfoundation.generator.transaction.box.TokenIssuingBox.TokenId

import scala.util.Try

case class TokenIssuingBox(override val proposition: EncryProposition,
                           override val nonce: Long,
                           override val amount: Long,
                           tokenId: TokenId) extends EncryBox[EncryProposition] with MonetaryBox {

  override val typeId: Byte = AssetBox.TypeId
}

object TokenIssuingBox {

  type TokenId = Array[Byte]

  val TypeId: Byte = 3.toByte

  implicit val jsonEncoder: Encoder[TokenIssuingBox] = (bx: TokenIssuingBox) => Map(
    "type"        -> TypeId.asJson,
    "id"          -> Algos.encode(bx.id).asJson,
    "proposition" -> bx.proposition.asJson,
    "nonce"       -> bx.nonce.asJson
  ).asJson
}

object AssetIssuingBoxSerializer extends Serializer[TokenIssuingBox] {

  override def toBytes(obj: TokenIssuingBox): Array[Byte] = {
    val propBytes: Array[Byte] = EncryPropositionSerializer.toBytes(obj.proposition)
    Bytes.concat(
      Shorts.toByteArray(propBytes.length.toShort),
      propBytes,
      Longs.toByteArray(obj.nonce),
      Longs.toByteArray(obj.amount),
      obj.tokenId
    )
  }

  override def parseBytes(bytes: Array[Byte]): Try[TokenIssuingBox] = Try {
    val propositionLen: Short         = Shorts.fromByteArray(bytes.take(2))
    val iBytes: Array[Byte]           = bytes.drop(2)
    val proposition: EncryProposition = EncryPropositionSerializer.parseBytes(iBytes.take(propositionLen)).get
    val nonce: Long                   = Longs.fromByteArray(iBytes.slice(propositionLen, propositionLen + 8))
    val amount: Long                  = Longs.fromByteArray(iBytes.slice(propositionLen + 8, propositionLen + 8 + 8))
    val creationId: TokenId           = bytes.takeRight(32)
    TokenIssuingBox(proposition, nonce, amount, creationId)
  }
}