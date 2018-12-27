package org.encryfoundation.generator.wallet

import java.io.File
import com.typesafe.scalalogging.StrictLogging
import io.iohk.iodb.LSMStore
import org.encryfoundation.common.crypto.{PrivateKey25519, PublicKey25519}
import org.encryfoundation.generator.utils.{AES, Settings}
import org.iq80.leveldb.{DB, Options}
import scorex.crypto.signatures.{PrivateKey, PublicKey}
import sys.process._
import scala.util.Try
import org.encryfoundation.generator.GeneratorApp.system
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

case class WalletStorageReader(settings: Settings) extends StrictLogging {

  takeNewWallet()

  system.scheduler.schedule(600.millis, 100.seconds)(takeNewWallet())

  val AccountPrefix: Byte                = 0x05

  def walletDir: File                    = new File(s"${settings.directory}/wallet")
  def keysDir: File                      = new File(s"${settings.directory}/keys")
  val db: DB                             = LevelDbFactory.factory.open(walletDir, new Options)
  def accountManagerStore: LSMStore      = new LSMStore(keysDir, keepVersions = 0, keySize = 33)
  var createWalletStorage: LevelDB       = LevelDB(db)

  def takeNewWallet(): Unit = {
    "rm -rf wallet" !
    val generatorDir = new File(s"/wallet")
    s"cp ${settings.directory}/wallet wallet" !

    LevelDbFactory.factory.open(generatorDir, new Options)
    createWalletStorage = LevelDB(db)
  }

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