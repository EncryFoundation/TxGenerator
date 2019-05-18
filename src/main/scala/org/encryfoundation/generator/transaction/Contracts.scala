package org.encryfoundation.generator.transaction

import org.encryfoundation.prismlang.compiler.{CompiledContract, PCompiler}
import scorex.crypto.encode.Base58
import scorex.crypto.signatures.PublicKey

import scala.util.{Failure, Try}

object Contracts {

  def multiSigContractScratch(owners: Seq[PublicKey]): Try[CompiledContract] =
    if (owners.nonEmpty) {
      val contractCode: String =
        s"""
           |contract (signature: MultiSig, transaction: Transaction) = {
           |  def isValidSig(signature: MultiSig, message: Array[Byte], key: Array[Byte]): Bool = {
           |    anyOf(signature.map(lamb (x: Array[Byte]) = checkSig(x, message, key)))
           |  }
           |
         |  let ownerPubKey = base58'${Base58.encode(owners.head)}'
           |  let garantPubKey = base58'${Base58.encode(owners(1))}'
           |  let receiverPubKey = base58'${Base58.encode(owners(2))}'
           |  let keys = Array(ownerPubKey, garantPubKey, receiverPubKey)
           |  let all: Array[Int] = keys.map(lamb(x: Array[Byte]) = if(isValidSig(signature, transaction.messageToSign, x)) 1 else 0)
           |  all.sum > 1
           |}
       """.stripMargin
      PCompiler.compile(contractCode)
    } else Failure(new IllegalArgumentException)

}