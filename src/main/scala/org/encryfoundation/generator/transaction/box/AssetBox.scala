package org.encryfoundation.generator.transaction.box

import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor}
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.transaction.box.TokenIssuingBox.TokenId

/** Represents monetary asset of some type locked with some `proposition`.
  * `tokenIdOpt = None` if the asset is of intrinsic type. */
case class AssetBox(override val proposition: EncryProposition,
                    override val nonce: Long,
                    override val amount: Long,
                    tokenIdOpt: Option[TokenId] = None) extends MonetaryBox {

  override val typeId: Byte = AssetBox.TypeId

  lazy val isIntrinsic: Boolean = tokenIdOpt.isEmpty
}

object AssetBox {

  val TypeId: Byte = 1.toByte

  implicit val jsonEncoder: Encoder[AssetBox] = (bx: AssetBox) => Map(
    "type" -> TypeId.asJson,
    "id" -> Algos.encode(bx.id).asJson,
    "proposition" -> bx.proposition.asJson,
    "nonce" -> bx.nonce.asJson,
    "value" -> bx.amount.asJson,
    "tokenId" -> bx.tokenIdOpt.map(id => Algos.encode(id)).asJson
  ).asJson

  implicit val jsonDecoder: Decoder[AssetBox] = (c: HCursor) => for {
    nonce <- c.downField("nonce").as[Long]
    proposition <- c.downField("proposition").as[EncryProposition]
    value <- c.downField("value").as[Long]
    tokenIdOpt <- c.downField("tokenId").as[Option[TokenId]](Decoder.decodeOption(Decoder.decodeString.emapTry(Algos.decode)))
  } yield AssetBox(proposition, nonce, value, tokenIdOpt)
}
