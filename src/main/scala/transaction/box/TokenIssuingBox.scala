package transaction.box

import io.circe.Encoder
import io.circe.syntax._
import org.encryfoundation.common.Algos
import transaction.box.TokenIssuingBox.TokenId

case class TokenIssuingBox(override val proposition: EncryProposition,
                           override val nonce: Long,
                           override val amount: Long,
                           tokenId: TokenId) extends MonetaryBox {

  override val typeId: Byte = AssetBox.TypeId
}

object TokenIssuingBox {

  type TokenId = Array[Byte]

  val TypeId: Byte = 3.toByte

  implicit val jsonEncoder: Encoder[TokenIssuingBox] = (bx: TokenIssuingBox) => Map(
    "type" -> TypeId.asJson,
    "id" -> Algos.encode(bx.id).asJson,
    "proposition" -> bx.proposition.asJson,
    "nonce" -> bx.nonce.asJson
  ).asJson
}
