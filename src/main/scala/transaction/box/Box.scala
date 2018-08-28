package transaction.box

import com.google.common.primitives.Longs
import io.circe.Encoder
import org.encryfoundation.common.Algos
import org.encryfoundation.common.utils.TaggedTypes.ADKey

trait Box {

  val proposition: EncryProposition

  val typeId: Byte

  val nonce: Long

  lazy val id: ADKey = ADKey @@ Algos.hash(Longs.toByteArray(nonce)).updated(0, typeId)

  def isAmountCarrying: Boolean = this.isInstanceOf[MonetaryBox]
}

object Box {

  implicit val jsonEncoder: Encoder[Box] = {
    case ab: AssetBox => AssetBox.jsonEncoder(ab)
    case db: DataBox => DataBox.jsonEncoder(db)
    case aib: TokenIssuingBox => TokenIssuingBox.jsonEncoder(aib)
  }
}
