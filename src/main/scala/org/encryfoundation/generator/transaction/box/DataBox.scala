package org.encryfoundation.generator.transaction.box

import io.circe.Encoder
import io.circe.syntax._
import org.encryfoundation.common.Algos

/** Stores arbitrary data in EncryTL binary format. */
case class DataBox(override val proposition: EncryProposition,
                   override val nonce: Long,
                   data: Array[Byte]) extends Box {

  override val typeId: Byte = DataBox.TypeId
}

object DataBox {

  val TypeId: Byte = 4.toByte

  implicit val jsonEncoder: Encoder[DataBox] = (bx: DataBox) => Map(
    "type" -> TypeId.asJson,
    "id" -> Algos.encode(bx.id).asJson,
    "proposition" -> bx.proposition.asJson,
    "nonce" -> bx.nonce.asJson,
    "data" -> Algos.encode(bx.data).asJson,
  ).asJson
}
