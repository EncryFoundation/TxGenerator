package org.encryfoundation.generator.utils

import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519
import scodec.bits.BitVector
import scorex.crypto.hash.Blake2b256
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}
import scala.io.Source

object Mnemonic {

  def createPrivKey(seed: Option[String]): PrivateKey25519 = {
    val (privateKey: PrivateKey, publicKey: PublicKey) = Curve25519.createKeyPair(
      Blake2b256.hash(
        seed.map {
          seedFromMnemonic(_)
        }
          .getOrElse {
            val phrase: String = entropyToMnemonicCode(scorex.utils.Random.randomBytes(16))
            seedFromMnemonic(phrase)
          })
    )
    PrivateKey25519(privateKey, publicKey)
  }

  private def seedFromMnemonic(mnemonicCode: String, passPhrase: String = ""): Array[Byte] =
    Algos.hash(mnemonicCode + "mnemonic=" + passPhrase)

  private def entropyToMnemonicCode(entropy: Array[Byte]): String = {
    val words: Array[String] =
      Source.fromInputStream(getClass.getResourceAsStream("/languages/english/words.txt")).getLines.toArray
    val checkSum: BitVector = BitVector(Algos.hash(entropy))
    val entropyWithCheckSum: BitVector = BitVector(entropy) ++ checkSum.take(4)

    entropyWithCheckSum.grouped(11).map { i =>
      words(i.toInt(signed = false))
    }.mkString(" ")
  }
}
