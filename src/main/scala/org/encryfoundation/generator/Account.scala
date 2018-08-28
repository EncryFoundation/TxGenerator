package org.encryfoundation.generator

import java.net.InetSocketAddress
import org.encryfoundation.common.crypto.PrivateKey25519
import scorex.crypto.hash.Blake2b256
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}
import scala.io.Source

case class Account(secret: PrivateKey25519, sourceNode: InetSocketAddress)

object Account {

  def parseFromFile(sourcePath: String): Seq[Account] = Source
    .fromInputStream(getClass.getResourceAsStream(sourcePath))
    .getLines()
    .map { line =>
      val splitLine: Array[String] = line.split(" | ")
      val (privKey: PrivateKey, pubKey: PublicKey) = Curve25519.createKeyPair(Blake2b256.hash(Mnemonic.seedFromMnemonic(splitLine.head)))
      val splitAddr: Array[String] = splitLine.last.split(":")
      Account(PrivateKey25519(privKey, pubKey), InetSocketAddress.createUnresolved(splitAddr.head, splitAddr.last.toInt))
    }
    .to[Seq]
}
