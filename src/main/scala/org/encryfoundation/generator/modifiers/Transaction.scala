package org.encryfoundation.generator.modifiers

import TransactionProto.TransactionProtoMessage
import com.google.protobuf.ByteString
import org.encryfoundation.generator.modifiers.box.Box
import org.encryfoundation.generator.modifiers.directives.{Directive, DirectiveProtoSerializer}
import scorex.crypto.hash.{Blake2b256, Digest32}
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor}
import org.encryfoundation.common.modifiers.mempool.transaction.{Input, InputSerializer, Proof, ProofSerializer}
import org.encryfoundation.common.utils.Algos
import org.encryfoundation.prismlang.core.Types
import org.encryfoundation.prismlang.core.wrapped.{PObject, PValue}

import scala.util.Try

case class Transaction(fee: Long,
                       timestamp: Long,
                       inputs: IndexedSeq[Input],
                       directives: IndexedSeq[Directive],
                       defaultProofOpt: Option[Proof]) {

  val messageToSign: Array[Byte] = UnsignedEncryTransaction.bytesToSign(fee, timestamp, inputs, directives)
  lazy val id: Array[Byte]       = Blake2b256.hash(messageToSign)
  lazy val newBoxes: IndexedSeq[Box] =
    directives.zipWithIndex.flatMap { case (d, idx) => d.boxes(Digest32 !@@ id, idx) }

  val tpe: Types.Product = Types.EncryTransaction

  def asVal: PValue = PValue(PObject(Map(
    "inputs"        -> PValue(inputs.map(_.boxId.toList), Types.PCollection(Types.PCollection.ofByte)),
    "outputs"       -> PValue(newBoxes.map(_.asPrism), Types.PCollection(Types.EncryBox)),
    "messageToSign" -> PValue(messageToSign, Types.PCollection.ofByte)
  ), tpe), tpe)
}

object Transaction {

  val modifierTypeId: Byte = 2.toByte

  implicit val jsonEncoder: Encoder[Transaction] = (tx: Transaction) => Map(
    "id"              -> Algos.encode(tx.id).asJson,
    "fee"             -> tx.fee.asJson,
    "timestamp"       -> tx.timestamp.asJson,
    "inputs"          -> tx.inputs.map(_.asJson).asJson,
    "directives"      -> tx.directives.map(_.asJson).asJson,
    "defaultProofOpt" -> tx.defaultProofOpt.map(_.asJson).asJson
  ).asJson

  implicit val jsonDecoder: Decoder[Transaction] = (c: HCursor) => {
    for {
      fee             <- c.downField("fee").as[Long]
      timestamp       <- c.downField("timestamp").as[Long]
      inputs          <- c.downField("inputs").as[IndexedSeq[Input]]
      directives      <- c.downField("directives").as[IndexedSeq[Directive]]
      defaultProofOpt <- c.downField("defaultProofOpt").as[Option[Proof]]
    } yield Transaction(
      fee,
      timestamp,
      inputs,
      directives,
      defaultProofOpt
    )
  }
}

trait ProtoTransactionSerializer[T] {

  def toProto(message: T): TransactionProtoMessage

  def fromProto(message: TransactionProtoMessage): Try[T]
}

object TransactionProtoSerializer extends ProtoTransactionSerializer[Transaction] {

  override def toProto(message: Transaction): TransactionProtoMessage = {
    val initialTx: TransactionProtoMessage = TransactionProtoMessage()
      .withFee(message.fee)
      .withTimestamp(message.timestamp)
      .withInputs(message.inputs.map(input => ByteString.copyFrom(input.bytes)).to[scala.collection.immutable.IndexedSeq])
      .withDirectives(message.directives.map(_.toDirectiveProto).to[scala.collection.immutable.IndexedSeq])
    message.defaultProofOpt match {
      case Some(value) => initialTx.withProof(ByteString.copyFrom(value.bytes))
      case None => initialTx
    }
  }

  override def fromProto(message: TransactionProtoMessage): Try[Transaction] = Try(Transaction(
    message.fee,
    message.timestamp,
    message.inputs.map(element => InputSerializer.parseBytes(element.toByteArray).get),
    message.directives.map(directive => DirectiveProtoSerializer.fromProto(directive).get),
    ProofSerializer.parseBytes(message.proof.toByteArray).toOption
  ))
}