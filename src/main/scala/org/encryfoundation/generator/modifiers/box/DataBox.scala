package org.encryfoundation.generator.modifiers.box

import com.google.common.primitives.{Bytes, Longs, Shorts}
import io.circe.{Decoder, Encoder, HCursor}
import io.circe.syntax._
import org.encryfoundation.common.serialization.Serializer
import org.encryfoundation.common.utils.Algos

import scala.util.Try

/** Stores arbitrary data in EncryTL binary format. */
case class DataBox(override val proposition: EncryProposition,
                   override val nonce: Long,
                   data: Array[Byte]) extends EncryBox[EncryProposition] {

  override val typeId: Byte = DataBox.TypeId
}

object DataBox {

  val TypeId: Byte = 4.toByte

  implicit val jsonEncoder: Encoder[DataBox] = (bx: DataBox) => Map(
    "type"        -> TypeId.asJson,
    "id"          -> Algos.encode(bx.id).asJson,
    "proposition" -> bx.proposition.asJson,
    "nonce"       -> bx.nonce.asJson,
    "data"        -> Algos.encode(bx.data).asJson,
  ).asJson

  implicit val jsonDecoder: Decoder[DataBox] = (c: HCursor) => {
    for {
      proposition <- c.downField("proposition").as[EncryProposition]
      nonce       <- c.downField("nonce").as[Long]
      data        <- c.downField("data").as[String]
    } yield DataBox(
      proposition,
      nonce,
      Algos.decode(data).getOrElse(Array.emptyByteArray)
    )
  }
}

object DataBoxSerializer extends Serializer[DataBox] {

  override def toBytes(obj: DataBox): Array[Byte] = {
    val propBytes: Array[Byte] = EncryPropositionSerializer.toBytes(obj.proposition)
    Bytes.concat(
      Shorts.toByteArray(propBytes.length.toShort),
      propBytes,
      Longs.toByteArray(obj.nonce),
      Shorts.toByteArray(obj.data.length.toShort),
      obj.data
    )
  }

  override def parseBytes(bytes: Array[Byte]): Try[DataBox] = Try {
    val propositionLen: Short         = Shorts.fromByteArray(bytes.take(2))
    val iBytes: Array[Byte]           = bytes.drop(2)
    val proposition: EncryProposition = EncryPropositionSerializer.parseBytes(iBytes.take(propositionLen)).get
    val nonce: Long                   = Longs.fromByteArray(iBytes.slice(propositionLen, propositionLen + 8))
    val dataLen: Short                = Shorts.fromByteArray(iBytes.slice(propositionLen + 8, propositionLen + 8 + 2))
    val data: Array[Byte]             = iBytes.takeRight(dataLen)
    DataBox(proposition, nonce, data)
  }
}