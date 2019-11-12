package org.encryfoundation.generator.transaction

import org.encryfoundation.common.crypto.{PrivateKey25519, PublicKey25519, Signature25519}
import org.encryfoundation.common.modifiers.mempool.directive.{Directive, TransferDirective}
import org.encryfoundation.common.modifiers.mempool.transaction.{Input, Proof, PubKeyLockedContract, Transaction}
import org.encryfoundation.common.modifiers.state.box.MonetaryBox
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.generator.modifiers.TransactionsFactory.{logger, prepareTransaction}
import org.encryfoundation.generator.modifiers.UnsignedEncryTransaction
import org.encryfoundation.prismlang.compiler.CompiledContract
import org.encryfoundation.prismlang.core.wrapped.BoxedValue

import scala.util.Random

object TransactionsFrame {

  def defaultPaymentTransaction(privKey: PrivateKey25519,
                                fee: Long,
                                timestamp: Long,
                                useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                                recipient: String,
                                numberOfCreatedDirectives: Int = 1,
                                tokenIdOpt: Option[ADKey] = None): Transaction = {
    val howMuchCanTransfer: Long = useOutputs.map(_._1.amount).sum - fee
    val howMuchWillTransfer: Long = howMuchCanTransfer - Math.abs(Random.nextLong % howMuchCanTransfer)
    val change: Long = howMuchCanTransfer - howMuchWillTransfer
    val directives: IndexedSeq[TransferDirective] =
      IndexedSeq(TransferDirective(recipient, howMuchWillTransfer, tokenIdOpt))
    prepareTransaction(privKey, fee, timestamp, useOutputs, directives, change, tokenIdOpt)
  }

  private def prepareTransaction(privKey: PrivateKey25519,
                                 fee: Long,
                                 timestamp: Long,
                                 useOutputs: Seq[(MonetaryBox, Option[(CompiledContract, Seq[Proof])])],
                                 directivesSeq: IndexedSeq[Directive],
                                 change: Long,
                                 tokenIdOpt: Option[ADKey] = None): Transaction = {

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
      throw new RuntimeException("Transaction impossible: required amount is bigger than available")
    }

    val directives: IndexedSeq[Directive] =
      if (change > 0) directivesSeq ++: IndexedSeq(TransferDirective(pubKey.address.address, change, tokenIdOpt))
      else directivesSeq

    val uTransaction: UnsignedEncryTransaction = UnsignedEncryTransaction(fee, timestamp, uInputs, directives)
    val signature: Signature25519              = privKey.sign(uTransaction.messageToSign)
    val proofs: IndexedSeq[Seq[Proof]]         = useOutputs.flatMap(_._2.map(_._2)).toIndexedSeq

    uTransaction.toSigned(proofs, Some(Proof(BoxedValue.Signature25519Value(signature.bytes.toList))))
  }
}
