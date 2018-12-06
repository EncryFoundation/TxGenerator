package org.encryfoundation.generator.wallet

import java.io.File
import io.iohk.iodb.LSMStore
import org.encryfoundation.common.crypto.PublicKey25519
import org.encryfoundation.generator.utils.Settings
import scorex.crypto.signatures.PublicKey

case class WalletStorageReader(settings: Settings) {

  val AccountPrefix: Byte = 0x05

  val walletDir: File = new File(s"${settings.directory}/wallet")

  val keysDir: File = new File(s"${settings.directory}/keys")

  val walletStore: LSMStore = new LSMStore(walletDir, keepVersions = 0)

  val accountManagerStore: LSMStore = new LSMStore(keysDir, keepVersions = 0, keySize = 33)

  val publicKeys: Set[PublicKey25519] =
    accountManagerStore.getAll().foldLeft(Seq.empty[PublicKey25519]) { case (acc, (k, _)) =>
      if (k.data.head == AccountPrefix) acc :+ PublicKey25519(PublicKey @@ k.data.tail)
      else acc
    }.toSet

  def createWalletStorage: WalletStorage = WalletStorage(walletStore, publicKeys)
}
