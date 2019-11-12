package org.encryfoundation.generator.storage

import cats.Apply
import cats.syntax.functor._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import io.chrisdavenport.log4cats.Logger
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.modifiers.mempool.transaction.PubKeyLockedContract
import org.encryfoundation.common.utils.Algos
import scorex.crypto.hash.Blake2b256
import scorex.crypto.signatures.{ Curve25519, PrivateKey, PublicKey }

final class ContractHashStorage[F[_]: Apply: Sync] private (
  logger: Logger[F],
  ref: Ref[F, List[String]]
) extends InMemoryStorage[F, String] {

  def createNewContactHash(mnemonic: String): F[Unit] =
    for {
      elem <- createContractHash(mnemonic)
      _    <- insert(elem)
    } yield ()

  def insert(elem: String): F[Unit] =
    ref.update(elem :: _) <*
      logger.info("Inserted new element into contract hash storage")

  def clean: F[Unit] =
    ref.set(List.empty[String]) <*
      logger.info("Contract hash storage cleaned up")

  def getAllElements: F[List[String]] =
    ref.get <*
      logger.info("Received all addresses from contract hash storage")

  def init: F[Unit] =
    (for {
      _ <- createNewContactHash(
            mnemonic = "boat culture ribbon wagon deposit decrease maid speak equal thunder have beauty"
          )
      _ <- createNewContactHash(
            mnemonic = "napkin they pyramid verb modify brave hurry agent will still easy great"
          )
    } yield ()) *> logger.info("Init contract hash storage")

  private def createContractHash(mnemonic: String): F[String] = {
    val (privateKey: PrivateKey, publicKey: PublicKey) =
      Curve25519.createKeyPair(Blake2b256.hash(Algos.hash(mnemonic + "mnemonic=")))
    val privateKey25519: PrivateKey25519 = PrivateKey25519(privateKey, publicKey)
    Sync[F].pure(Algos.encode(PubKeyLockedContract(privateKey25519.publicImage.pubKeyBytes).contract.hash))
  }
}

object ContractHashStorage {
  def apply[F[_]: Sync](logger: Logger[F]): F[ContractHashStorage[F]] =
    Ref[F].of(List.empty[String]).map(ref => new ContractHashStorage(logger, ref))
}
