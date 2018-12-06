package org.encryfoundation.generator.wallet

import java.io.File
import com.typesafe.scalalogging.StrictLogging
import io.iohk.iodb.LSMStore
import org.encryfoundation.common.crypto.{PrivateKey25519, PublicKey25519}
import org.encryfoundation.generator.utils.{AES, Settings}
import scorex.crypto.signatures.{PrivateKey, PublicKey}
import scala.util.Try

case class WalletStorageReader(settings: Settings) extends StrictLogging {

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

  def accounts: List[PrivateKey25519] =
    accountManagerStore.getAll().foldLeft(List[PrivateKey25519]()) { case (acc, (k, v)) =>
      if (k.data.head == AccountPrefix)
        acc :+ PrivateKey25519(PrivateKey @@ decrypt(v.data), PublicKey @@ k.data.tail)
      else acc
    }

  private def decrypt(data: Array[Byte]): Array[Byte] = Try(AES.decrypt(data, settings.walletSettings.password))
    .fold(e => {
      logger.info(s"AccountManager: decryption failed cause ${e.getCause}")
      sys.exit(999)
    }, r => r)

}
