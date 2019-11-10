package org.encryfoundation.generator

import cats.Parallel
import cats.effect.{ Async, Sync, Timer }
import io.chrisdavenport.log4cats.Logger
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.instances.list._
import org.encryfoundation.generator.storage.{ BatchesStorage, ContractHashStorage }
import fs2.Stream
import scala.concurrent.duration._

final class BoxesProcessor[F[_]: Async: Parallel: Timer] private (
  logger: Logger[F],
  batchesStorage: BatchesStorage[F],
  contractHashStorage: ContractHashStorage[F]
) {

  def run: Stream[F, Unit] = Stream(()).repeat.covary[F].metered(2.seconds).evalMap[F, Unit](_ => createNewTransaction)

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
}

object BoxesProcessor {
  def apply[F[_]: Async: Parallel: Timer](
    logger: Logger[F],
    batchesStorage: BatchesStorage[F],
    contractHashStorage: ContractHashStorage[F]
  ): F[BoxesProcessor[F]] =
    Sync[F].pure(new BoxesProcessor[F](logger, batchesStorage, contractHashStorage))
}
