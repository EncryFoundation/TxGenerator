package org.encryfoundation.generator.wallet

import java.io.File
import com.typesafe.scalalogging.StrictLogging
import io.iohk.iodb.LSMStore
import org.encryfoundation.common.crypto.{PrivateKey25519, PublicKey25519}
import org.encryfoundation.generator.utils.{AES, Settings}
import org.iq80.leveldb.{DB, Options}
import scorex.crypto.signatures.{PrivateKey, PublicKey}
import scala.util.Try

case class WalletStorageReader(settings: Settings) extends StrictLogging {

  val AccountPrefix: Byte                = 0x05

  def walletDir: File                    = new File(s"${settings.directory}/wallet")
  def keysDir: File                      = new File(s"${settings.directory}/keys")
  val db: DB                             = LevelDbFactory.factory.open(walletDir, new Options)
  def accountManagerStore: LSMStore      = new LSMStore(keysDir, keepVersions = 0, keySize = 33)
  def createWalletStorage: LevelDB       = LevelDB(db)

  val publicKeys: Set[PublicKey25519]    = accountManagerStore.getAll().foldLeft(Seq.empty[PublicKey25519]) {
    case (acc, (k, _)) =>
      if (k.data.head == AccountPrefix) acc :+ PublicKey25519(PublicKey @@ k.data.tail)
      else acc
    }.toSet

  def accounts: List[PrivateKey25519]    = accountManagerStore.getAll().foldLeft(List[PrivateKey25519]()) {
    case (acc, (k, v)) =>
      if (k.data.head == AccountPrefix) acc :+ PrivateKey25519(PrivateKey @@ decrypt(v.data), PublicKey @@ k.data.tail)
      else acc
    }

  private def decrypt(data: Array[Byte]): Array[Byte] = Try(AES.decrypt(data, settings.walletSettings.password))
    .fold(exception => {
      logger.info(s"AccountManager: decryption failed cause ${exception.getCause}")
      sys.exit(999)
    }, result => result)
}