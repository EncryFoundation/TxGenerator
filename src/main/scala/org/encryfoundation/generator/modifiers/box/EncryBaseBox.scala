package org.encryfoundation.generator.modifiers.box

import com.google.common.primitives.Longs
import io.circe.Encoder
import org.encryfoundation.common.Algos
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.prismlang.core.wrapped.{PObject, PValue}
import org.encryfoundation.prismlang.core.{PConvertible, Types}

trait EncryBaseBox extends Box with PConvertible {

  val typeId: Byte

  val nonce: Long

  override lazy val id: ADKey     = ADKey @@ Algos.hash(Longs.toByteArray(nonce)).updated(0, typeId)

  override val tpe: Types.Product = Types.EncryBox

  override def asVal: PValue      = PValue(asPrism, tpe)

  override lazy val baseFields: Map[String, PValue] = Map(
    "contractHash" -> PValue(proposition.contractHash, Types.PCollection.ofByte),
    "typeId"       -> PValue(typeId.toLong, Types.PInt),
    "id"           -> PValue(id, Types.PCollection.ofByte)
  )

  override def asPrism: PObject = PObject(baseFields, tpe)
}

object EncryBaseBox {

  implicit val jsonEncoder: Encoder[EncryBaseBox] = {
    case ab: AssetBox         => AssetBox.jsonEncoder(ab)
    case db: DataBox          => DataBox.jsonEncoder(db)
    case aib: TokenIssuingBox => TokenIssuingBox.jsonEncoder(aib)
  }
}