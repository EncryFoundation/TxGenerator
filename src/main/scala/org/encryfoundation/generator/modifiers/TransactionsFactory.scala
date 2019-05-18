package org.encryfoundation.generator.modifiers

import com.google.common.primitives.{Bytes, Longs}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.crypto.{PrivateKey25519, PublicKey25519, Signature25519}
import org.encryfoundation.common.transaction.{Input, Proof, PubKeyLockedContract}
import org.encryfoundation.prismlang.compiler.CompiledContract
import org.encryfoundation.prismlang.core.wrapped.{BoxedValue, PObject, PValue}
import org.encryfoundation.common.Algos
import org.encryfoundation.prismlang.core.wrapped.BoxedValue
import scorex.crypto.hash.{Blake2b256, Digest32}
import org.encryfoundation.generator.modifiers.directives._
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.generator.transaction.box.{Box, MonetaryBox}
import org.encryfoundation.prismlang.core.Types

import org.encryfoundation.generator.modifiers.box.MonetaryBox
import scala.util.Random

case class UnsignedTransaction(fee: Long,
                               timestamp: Long,
                               inputs: IndexedSeq[Input],
                               directives: IndexedSeq[Directive]) {

case class EncryTransaction(fee: Long,
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

object EncryTransaction {

  implicit val jsonEncoder: Encoder[EncryTransaction] = (tx: EncryTransaction) => Map(
    "id"              -> Algos.encode(tx.id).asJson,
    "fee"             -> tx.fee.asJson,
    "timestamp"       -> tx.timestamp.asJson,
    "inputs"          -> tx.inputs.map(_.asJson).asJson,
    "directives"      -> tx.directives.map(_.asJson).asJson,
    "defaultProofOpt" -> tx.defaultProofOpt.map(_.asJson).asJson
  ).asJson

  implicit val jsonDecoder: Decoder[EncryTransaction] = (c: HCursor) => {
    for {
      fee             <- c.downField("fee").as[Long]
      timestamp       <- c.downField("timestamp").as[Long]
      inputs          <- c.downField("inputs").as[IndexedSeq[Input]]
      directives      <- c.downField("directives").as[IndexedSeq[Directive]]
      defaultProofOpt <- c.downField("defaultProofOpt").as[Option[Proof]]
    } yield EncryTransaction(
      fee,
      timestamp,
      inputs,
      directives,
      defaultProofOpt
    )
  }
}

case class UnsignedEncryTransaction(fee: Long,
                                    timestamp: Long,
                                    inputs: IndexedSeq[Input],
                                    directives: IndexedSeq[Directive]) {

  val messageToSign: Array[Byte] = UnsignedTransaction.bytesToSign(fee, timestamp, inputs, directives)

  def toSigned(proofs: IndexedSeq[Seq[Proof]], defaultProofOpt: Option[Proof]): Transaction = {
    val signedInputs: IndexedSeq[Input] = inputs.zipWithIndex.map { case (input, idx) =>
      if (proofs.nonEmpty && proofs.isDefinedAt(idx)) input.copy(proofs = proofs(idx).toList) else input
    }
    Transaction(fee, timestamp, signedInputs, directives, defaultProofOpt)
  }
}

object UnsignedTransaction {

  def bytesToSign(fee: Long,
                  timestamp: Long,
                  inputs: IndexedSeq[Input],
                  directives: IndexedSeq[Directive]): Digest32 =
    Blake2b256.hash(Bytes.concat(
      inputs.flatMap(_.bytesWithoutProof).toArray,
      directives.flatMap(_.bytes).toArray,
      Longs.toByteArray(timestamp),
      Longs.toByteArray(fee)
    ))
}

object TransactionsFactory extends StrictLogging {

  def defaultPaymentTransaction(privKey: PrivateKey25519,
                                fee: Long,
                                timestamp: Long,
                                useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                                recipient: String,
                                amount: Long,
                                numberOfCreatedDirectives: Int = 1,
                                tokenIdOpt: Option[ADKey] = None): Transaction = {
    val howMuchCanTransfer: Long = useOutputs.map(_._1.amount).sum - fee
    val howMuchWillTransfer: Long = howMuchCanTransfer - Math.abs(Random.nextLong % howMuchCanTransfer)
    val change: Long = howMuchCanTransfer - howMuchWillTransfer
    logger.info(s"howMuchCanTransfer - $howMuchCanTransfer. howMuchWillTransfer - $howMuchWillTransfer. " +
      s"Change - $change")
    val directives: IndexedSeq[TransferDirective] =
      IndexedSeq(TransferDirective(recipient, howMuchWillTransfer, tokenIdOpt))
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, change, tokenIdOpt)
  }

  def defaultPaymentTransactionWithoutRandom(privKey: PrivateKey25519,
                                             fee: Long,
                                             timestamp: Long,
                                             useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                                             recipient: String,
                                             amount: Long,
                                             numberOfCreatedDirectives: Int = 1,
                                             tokenIdOpt: Option[ADKey] = None): EncryTransaction = {
    val howMuchCanTransfer: Long = useOutputs.map(_._1.amount).sum - fee
    val change: Long = howMuchCanTransfer - amount
    val directives: IndexedSeq[TransferDirective] =
      IndexedSeq(TransferDirective(recipient, amount, tokenIdOpt))
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, change, tokenIdOpt)
  }

  def scriptedAssetTransactionScratch(privKey: PrivateKey25519,
                                      fee: Long,
                                      timestamp: Long,
                                      useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                                      contract: CompiledContract,
                                      amount: Long,
                                      numberOfCreatedDirectives: Int = 1,
                                      tokenIdOpt: Option[ADKey] = None): Transaction = {
    val directives: IndexedSeq[ScriptedAssetDirective] =
      (1 to numberOfCreatedDirectives).foldLeft(IndexedSeq.empty[ScriptedAssetDirective]) { case (directivesAll, _) =>
        directivesAll :+ ScriptedAssetDirective(contract.hash, amount, tokenIdOpt)
      }
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, amount, tokenIdOpt)
  }

  def assetIssuingTransactionScratch(privKey: PrivateKey25519,
                                     fee: Long,
                                     timestamp: Long,
                                     useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                                     contract: CompiledContract,
                                     amount: Long,
                                     numberOfCreatedDirectives: Int = 1,
                                     tokenIdOpt: Option[ADKey] = None): Transaction = {
    val directives: IndexedSeq[AssetIssuingDirective] =
      (1 to numberOfCreatedDirectives).foldLeft(IndexedSeq.empty[AssetIssuingDirective]) { case (directivesAll, _) =>
        directivesAll :+ AssetIssuingDirective(contract.hash, amount)
      }
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, amount, tokenIdOpt)
  }

  def dataTransactionScratch(privKey: PrivateKey25519,
                             fee: Long,
                             timestamp: Long,
                             useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                             contract: CompiledContract,
                             amount: Long,
                             data: Array[Byte],
                             numberOfCreatedDirectives: Int = 1,
                             tokenIdOpt: Option[ADKey] = None): Transaction = {
    val directives: IndexedSeq[DataDirective] =
      (1 to numberOfCreatedDirectives).foldLeft(IndexedSeq.empty[DataDirective]) { case (directivesAll, _) =>
        directivesAll :+ DataDirective(contract.hash, data)
      }
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, amount, tokenIdOpt)
  }

  private def prepareTransaction(privKey: PrivateKey25519,
                                 fee: Long,
                                 timestamp: Long,
                                 useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                                 directivesSeq: IndexedSeq[Directive],
                                 change: Long,
                                 tokenIdOpt: Option[ADKey] = None): EncryTransaction = {

    val pubKey: PublicKey25519 = privKey.publicImage

    val uInputs: IndexedSeq[Input] = useOutputs.toIndexedSeq.map { case (box, contractOpt) =>
      Input.unsigned(
        box.id,
        contractOpt match {
          case Some((ct, _)) => Left(ct)
          case None => Right(PubKeyLockedContract(pubKey.pubKeyBytes))
        }
      )
    }

    if (change < 0) {
      logger.warn(s"Transaction impossible: required amount is bigger than available. Change is: $change.")
      throw new RuntimeException("Transaction impossible: required amount is bigger than available")
    }

    val directives: IndexedSeq[Directive] =
      if (change > 0) directivesSeq ++: IndexedSeq(TransferDirective(pubKey.address.address, change, tokenIdOpt))
      else directivesSeq

    val uTransaction: UnsignedTransaction = UnsignedTransaction(fee, timestamp, uInputs, directives)
    val signature: Signature25519              = privKey.sign(uTransaction.messageToSign)
    val proofs: IndexedSeq[Seq[Proof]]         = useOutputs.flatMap(_._2.map(_._2)).toIndexedSeq

    uTransaction.toSigned(proofs, Some(Proof(BoxedValue.Signature25519Value(signature.bytes.toList))))
  }
}