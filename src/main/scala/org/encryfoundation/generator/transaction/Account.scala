package org.encryfoundation.generator.transaction

import java.net.InetSocketAddress
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519
import scorex.crypto.hash.Blake2b256
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}

case class Account(secret: PrivateKey25519, sourceNode: InetSocketAddress)

object Account {

  def parseFromSettings(sourcePath: String): Seq[Account] = {
    val splitLine: Array[String] = sourcePath.split("::")
    val (privKey: PrivateKey, pubKey: PublicKey) = Curve25519.createKeyPair(
      Blake2b256.hash(Algos.hash(splitLine.head + "mnemonic=")))
    val splitAddr: Array[String] = splitLine.last.split(":")
    Seq(Account(PrivateKey25519(privKey, pubKey), InetSocketAddress.createUnresolved(splitAddr.head, splitAddr.last.toInt)))
  }
}
