package org.encryfoundation.generator.modifiers

import com.google.common.primitives.{Bytes, Longs}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.{PrivateKey25519, PublicKey25519, Signature25519}
import org.encryfoundation.common.transaction.{Input, Proof, PubKeyLockedContract}
import org.encryfoundation.prismlang.compiler.CompiledContract
import org.encryfoundation.prismlang.core.wrapped.BoxedValue
import scorex.crypto.hash.{Blake2b256, Digest32}
import org.encryfoundation.generator.modifiers.directives._
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.generator.modifiers.box.MonetaryBox

import scala.util.Random

case class UnsignedTransaction(fee: Long,
                               timestamp: Long,
                               inputs: IndexedSeq[Input],
                               directives: IndexedSeq[Directive]) extends StrictLogging {

  val messageToSign: Array[Byte] = UnsignedTransaction.bytesToSign(fee, timestamp, inputs, directives)

  def toSigned(proofs: IndexedSeq[Seq[Proof]],
               defaultProofOpt: Option[Proof],
               howMuch: Long,
               howLong: Long): Transaction = {
    val signedInputs: IndexedSeq[Input] = inputs.zipWithIndex.map { case (input, idx) =>
      if (proofs.nonEmpty && proofs.lengthCompare(idx + 1) <= 0) input.copy(proofs = proofs(idx).toList) else input
    }
    val s = Transaction(fee, timestamp, signedInputs, directives, defaultProofOpt)
    logger.info(s"Tx id: ${Algos.encode(s.id)}: howMCan: $howMuch, howWill: $howLong. " +
      s"${s.inputs.map(x => Algos.encode(x.boxId))}")
    s
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
    logger.debug(s"howMuchCanTransfer - $howMuchCanTransfer. howMuchWillTransfer - $howMuchWillTransfer. " +
      s"Change - $change")
    val directives: IndexedSeq[TransferDirective] =
      IndexedSeq(TransferDirective(recipient, howMuchWillTransfer, tokenIdOpt))
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, change, tokenIdOpt, howMuchCanTransfer, howMuchWillTransfer)
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
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, amount, tokenIdOpt,0,0)
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
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, amount, tokenIdOpt, 0,0)
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
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, amount, tokenIdOpt, 0, 0)
  }

  private def prepareTransaction(privKey: PrivateKey25519,
                                 fee: Long,
                                 timestamp: Long,
                                 useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                                 directivesSeq: IndexedSeq[Directive],
                                 amount: Long,
                                 tokenIdOpt: Option[ADKey] = None,
                                 howCan: Long,
                                 howWill: Long): Transaction = {

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

    val change: Long = amount

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

    uTransaction.toSigned(proofs, Some(Proof(BoxedValue.Signature25519Value(signature.bytes.toList))), howCan, howWill)
  }
}