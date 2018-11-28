package org.encryfoundation.generator.transaction

import java.net.InetSocketAddress
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.generator.utils.AccountsSettings
import scorex.crypto.hash.Blake2b256
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}

case class Account(secret: PrivateKey25519, sourceNode: InetSocketAddress)

object Account {

  def parseFromSettings(accounts: List[AccountsSettings]): Seq[Account] =
    accounts.map { account =>
//      val (privKey: PrivateKey, pubKey: PublicKey) = Curve25519.createKeyPair(
//        Blake2b256.hash(Algos.hash(account.mnemonic + "mnemonic=")))
      val privKey = PrivateKey @@ Algos.decode(account.privateKey).get
      val pubKey = PublicKey @@ Algos.decode(account.publicKey).get
      Account(PrivateKey25519(privKey, pubKey), InetSocketAddress.createUnresolved(account.node.host, account.node.port))
    }
}