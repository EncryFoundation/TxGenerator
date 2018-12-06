package org.encryfoundation.generator.transaction

import java.net.InetSocketAddress
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.generator.utils.AccountsSettings
import scorex.crypto.signatures
import scorex.crypto.signatures.{PrivateKey, PublicKey}
import supertagged.@@

case class Account(secret: PrivateKey25519, sourceNode: InetSocketAddress)

object Account {

  def parseFromSettings(accounts: List[AccountsSettings]): Seq[Account] =
    accounts.map { account =>
      val privKey: @@[Array[Byte], signatures.PrivateKey.Tag] =
        PrivateKey @@ Algos.decode(account.privateKey).getOrElse(Array.emptyByteArray)
      val pubKey: @@[Array[Byte], signatures.PublicKey.Tag] =
        PublicKey @@ Algos.decode(account.publicKey).getOrElse(Array.emptyByteArray)
      Account(PrivateKey25519(privKey, pubKey), InetSocketAddress.createUnresolved(account.node.host, account.node.port))
    }
}