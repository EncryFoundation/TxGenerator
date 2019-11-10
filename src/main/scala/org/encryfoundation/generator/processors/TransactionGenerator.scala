package org.encryfoundation.generator.processors

import cats.Parallel
import cats.effect.{Async, Sync, Timer}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.encryfoundation.generator.actors.BoxesBatch
import org.encryfoundation.generator.storage.{BatchesStorage, ContractHashStorage, TransactionsStorage}

import scala.concurrent.duration._

final class TransactionGenerator[F[_]: Async: Parallel: Timer] private (
  logger: Logger[F],
  batchesStorage: BatchesStorage[F],
  contractHashStorage: ContractHashStorage[F],
  transactionsStorage: TransactionsStorage[F]
) {

  def run: Stream[F, Unit] =
    Stream(()).repeat
      .covary[F]
      .metered(2.seconds)
      .evalMap[F, Unit](_ => createNewTransaction)

  private def createNewTransaction: F[Unit] =
    for {
      _    <- logger.info("Init createNewTransaction function")
      keys <- contractHashStorage.getAllAddresses
      _    <- keys.map(key => processBatch(key)).parSequence
    } yield ()

  private def processBatch(key: String): F[Unit] =
    for {
      _     <- logger.info("Init processBatch function")
      batch <- batchesStorage.getMany(key, 5)
      _     <- logger.info(s"Got ${batch.size} batches from storage")
    } yield ()

  private def createNewTransaction(batch: BoxesBatch) =
    for {

    }
}

object TransactionGenerator {
  def apply[F[_]: Async: Parallel: Timer](
    logger: Logger[F],
    batchesStorage: BatchesStorage[F],
    contractHashStorage: ContractHashStorage[F],
    transactionsStorage: TransactionsStorage[F]
  ): F[TransactionGenerator[F]] =
    Sync[F].pure(new TransactionGenerator[F](logger, batchesStorage, contractHashStorage, transactionsStorage))
}
