package org.encryfoundation.generator.modifiers.box

import com.google.common.primitives.{Bytes, Longs, Shorts}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor}
import org.encryfoundation.common.Algos
import org.encryfoundation.common.serialization.Serializer
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.generator.modifiers.box.TokenIssuingBox.TokenId

import scala.util.Try

/** Represents monetary asset of some type locked with some `proposition`.
  * `tokenIdOpt = None` if the asset is of intrinsic type. */
case class AssetBox(override val proposition: EncryProposition,
                    override val nonce: Long,
                    override val amount: Long,
                    tokenIdOpt: Option[TokenId] = None) extends EncryBox[EncryProposition] with MonetaryBox {

  override val typeId: Byte     = AssetBox.TypeId

  lazy val isIntrinsic: Boolean = tokenIdOpt.isEmpty
}

object AssetBox {

  val IntrinsicTokenId: String = Algos.encode(Algos.hash("intrinsic_token"))

  def apply(proposition: EncryProposition,
            nonce: Long,
            amount: Long,
            tokenIdOpt: Option[TokenId] = None): AssetBox = new AssetBox(
    proposition,
    nonce,
    amount,
    tokenIdOpt match {
      case Some(id) if Algos.encode(id) == IntrinsicTokenId => Option.empty[TokenId]
      case ex => ex
    }
  )

  val TypeId: Byte = 1.toByte

  implicit val jsonEncoder: Encoder[AssetBox] = (bx: AssetBox) => Map(
    "type"        -> TypeId.asJson,
    "id"          -> Algos.encode(bx.id).asJson,
    "proposition" -> bx.proposition.asJson,
    "nonce"       -> bx.nonce.asJson,
    "value"       -> bx.amount.asJson,
    "tokenId"     -> bx.tokenIdOpt.map(id => Algos.encode(id)).asJson
  ).asJson

  implicit val jsonDecoder: Decoder[AssetBox] = (c: HCursor) => for {
    nonce       <- c.downField("nonce").as[Long]
    proposition <- c.downField("proposition").as[EncryProposition]
    value       <- c.downField("value").as[Long]
    tokenIdOpt  <- c.downField("tokenId")
      .as[Option[TokenId]](Decoder.decodeOption(Decoder.decodeString.emapTry(Algos.decode)))
  } yield AssetBox.apply(proposition, nonce, value, tokenIdOpt)
}

object AssetBoxSerializer extends Serializer[AssetBox] {

  override def toBytes(obj: AssetBox): Array[Byte] = {
    val propBytes = EncryPropositionSerializer.toBytes(obj.proposition)
    Bytes.concat(
      Shorts.toByteArray(propBytes.length.toShort),
      propBytes,
      Longs.toByteArray(obj.nonce),
      Longs.toByteArray(obj.amount),
      obj.tokenIdOpt.getOrElse(Array.empty)
    )
  }

  override def parseBytes(bytes: Array[Byte]): Try[AssetBox] = Try {
    val propositionLen: Short         = Shorts.fromByteArray(bytes.take(2))
    val iBytes: Array[Byte]           = bytes.drop(2)
    val proposition: EncryProposition = EncryPropositionSerializer.parseBytes(iBytes.take(propositionLen)).get
    val nonce: Long                   = Longs.fromByteArray(iBytes.slice(propositionLen, propositionLen + 8))
    val amount: Long                  = Longs.fromByteArray(iBytes.slice(propositionLen + 8, propositionLen + 8 + 8))
    val tokenIdOpt: Option[TokenId]   =
      if ((iBytes.length - (propositionLen + 8 + 8)) == 32) Some(iBytes.takeRight(32)) else None
    AssetBox(proposition, nonce, amount, tokenIdOpt)
  }
}