package org.encryfoundation.generator.transaction

import org.encryfoundation.prismlang.compiler.{CompiledContract, PCompiler}
import scorex.crypto.encode.Base58
import scorex.crypto.signatures.PublicKey

import scala.util.{Failure, Try}

object Contracts {

  def multiSigContractScratch(owners: Seq[PublicKey], threshold: Int): Try[CompiledContract] =
  if (owners.size > 0) {
    val contractCode: String =
     s"""
        |contract (signature: MultiSig, transaction: Transaction) = {
        |  def isValidSig(signature: MultiSig, message: Array[Byte], key: Array[Byte]): Bool = {
        |    anyOf(signature.map(lamb (x: Array[Byte]) = checkSig(x, message, key)))
        |  }
        |
        |  let keys = Array(${if (owners.size > 1) owners.map("base58'" + Base58.encode(_) + "'").mkString(", ") else s"base58'${Base58.encode(owners(0))}'"})
        |  let sum = keys.map(lamb(x: Array[Byte]) = if(isValidSig(signature, transaction.messageToSign, x)) 1 else 0).sum
        |  sum >= $threshold
        |}
      """.stripMargin
    PCompiler.compile(contractCode)
  } else Failure(new IllegalArgumentException)
}
