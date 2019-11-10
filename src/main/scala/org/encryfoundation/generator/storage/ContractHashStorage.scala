package org.encryfoundation.generator.storage

import cats.Apply
import cats.syntax.functor._
import cats.syntax.apply._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import io.chrisdavenport.log4cats.Logger
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.modifiers.mempool.transaction.PubKeyLockedContract
import org.encryfoundation.common.utils.Algos
import org.encryfoundation.generator.utils.Mnemonic

final class ContractHashStorage[F[_]: Apply] private (
  logger: Logger[F],
  ref: Ref[F, List[String]]
) extends InMemoryStorage[F, String] {
  def insert(elem: String): F[Unit] = ref.update(elem :: _)

  def clean: F[Unit] = ref.set(List.empty[String])

  def getAllAddresses: F[List[String]] = ref.get <* logger.info("Called getAllKeys function")

  def init: F[Unit] =
    ref.set {
      val privateKey: PrivateKey25519 = Mnemonic.createPrivKey(
        Some("boat culture ribbon wagon deposit decrease maid speak equal thunder have beauty")
      )
      val privateKey2: PrivateKey25519 = Mnemonic.createPrivKey(
        Some("napkin they pyramid verb modify brave hurry agent will still easy great")
      )
      val contractHash: String  = Algos.encode(PubKeyLockedContract(privateKey.publicImage.pubKeyBytes).contract.hash)
      val contractHash2: String = Algos.encode(PubKeyLockedContract(privateKey2.publicImage.pubKeyBytes).contract.hash)
      List(contractHash, contractHash2)
    } *> logger.info("Init keys collection")
}

object ContractHashStorage {
  def apply[F[_]: Sync](logger: Logger[F]): F[ContractHashStorage[F]] =
    Ref[F].of(List.empty[String]).map(ref => new ContractHashStorage(logger, ref))
}
