package org.encryfoundation.generator.utils

import org.encryfoundation.generator.transaction.box._
import scala.util.{Failure, Try}

object StateModifierDeserializer {

  def parseBytes(bytes: Array[Byte], typeId: Byte): Try[EncryBaseBox] = typeId match {
    case AssetBox.`TypeId`        => AssetBoxSerializer.parseBytes(bytes)
    case TokenIssuingBox.`TypeId` => AssetIssuingBoxSerializer.parseBytes(bytes)
    case DataBox.`TypeId`         => DataBoxSerializer.parseBytes(bytes)
    case t                        => Failure(new Exception(s"Got unknown typeId: $t"))
  }
}