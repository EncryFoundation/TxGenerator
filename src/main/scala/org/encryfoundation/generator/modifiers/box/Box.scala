package org.encryfoundation.generator.modifiers.box

import com.google.common.primitives.Longs
import io.circe.{Decoder, DecodingFailure, Encoder}
import org.encryfoundation.common.utils.Algos
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.prismlang.core.Types
import org.encryfoundation.prismlang.core.wrapped.{PObject, PValue}

trait Box {

  val proposition: EncryProposition

  val typeId: Byte

  val nonce: Long

  lazy val id: ADKey = ADKey @@ Algos.hash(Longs.toByteArray(nonce)).updated(0, typeId)

  def isAmountCarrying: Boolean = this.isInstanceOf[MonetaryBox]

  val tpe: Types.Product = Types.EncryBox

  def asVal: PValue = PValue(asPrism, tpe)

  lazy val baseFields: Map[String, PValue] = Map(
    "contractHash" -> PValue(proposition.contractHash, Types.PCollection.ofByte),
    "typeId"       -> PValue(typeId.toLong, Types.PInt),
    "id"           -> PValue(id, Types.PCollection.ofByte)
  )

  def asPrism: PObject = PObject(baseFields, tpe)
}

object Box {

  implicit val jsonEncoder: Encoder[Box] = {
    case ab: AssetBox         => AssetBox.jsonEncoder(ab)
    case db: DataBox          => DataBox.jsonEncoder(db)
    case aib: TokenIssuingBox => TokenIssuingBox.jsonEncoder(aib)
  }

  implicit val jsonDecoder: Decoder[Box] = {
    Decoder.instance { c =>
      c.downField("type").as[Byte] match {
        case Right(s) => s match {
          case AssetBox.TypeId => AssetBox.jsonDecoder(c)
          case DataBox.TypeId  => DataBox.jsonDecoder(c)
          case _               => Left(DecodingFailure("Incorrect directive typeID", c.history))
        }
        case Left(_) => Left(DecodingFailure("None typeId", c.history))
      }
    }
  }
}
