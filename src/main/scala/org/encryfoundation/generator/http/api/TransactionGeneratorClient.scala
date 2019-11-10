package org.encryfoundation.generator.http.api

import cats.Parallel
import cats.effect.{ Async, ContextShift, Sync, Timer }
import cats.syntax.applicativeError._
import org.http4s.client.Client
import io.chrisdavenport.log4cats.Logger
import org.encryfoundation.generator.storage.{ BatchesStorage, ContractHashStorage }
import org.http4s.Uri
import org.http4s.circe._
import cats.syntax.apply._
import fs2.Stream
import org.encryfoundation.common.modifiers.state.box.AssetBox
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.semigroup._
import cats.syntax.parallel._
import cats.instances.list._
import org.encryfoundation.generator.actors.BoxesBatch
import cats.instances.long._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

final class TransactionGeneratorClient[F[_]: Async: Timer: ContextShift: Parallel](
  client: Client[F],
  logger: Logger[F],
  batchesStorage: BatchesStorage[F],
  contractHashStorage: ContractHashStorage[F]
)(implicit ec: ExecutionContext) {

  def run: Stream[F, Unit] = Stream(()).repeat.covary[F].metered(1.seconds).evalMap[F, Unit](_ => requestNewBoxes)

  private def requestNewBoxes: F[Unit] =
    for {
      addresses <- contractHashStorage.getAllAddresses
      _         <- logger.info(s"Start processing new boxes from api. Addresses storage size is ${addresses.size}")
      _         <- addresses.map(address => requestNUtxos(address, 0, 10)).parSequence
    } yield ()

  private def requestUtxoApi(contractHash: String, from: Int, to: Int): F[List[AssetBox]] =
    client
      .expect[List[AssetBox]](
        Uri.unsafeFromString(s"http://172.16.10.58:9000/wallet/$contractHash/boxes/$from/$to")
      )(jsonOf[F, List[AssetBox]])
      .handleErrorWith { f: Throwable =>
        logger.error(f)("While request utxos error has occurred") *> Sync[F].pure(List.empty)
      } <* logger.info(s"Handled new boxes from http api!")

  private def requestNUtxos(contractHash: String, from: Int, to: Int): F[Unit] =
    for {
      _       <- logger.info(s"Start requesting boxes for $contractHash")
      boxes   <- requestUtxoApi(contractHash, from, to)
      batches <- collectBatches(boxes)
      _       <- batchesStorage.insertMany(contractHash, batches)
      size    <- batchesStorage.getSizeByKey(contractHash)
      _ <- if (size > 100 || boxes.isEmpty)
            Sync[F].unit <* logger.info(
              s"Current number of boxes is $size. Received boxes size is ${boxes.size} Stop requesting new boxes"
            )
          else
            requestNUtxos(contractHash, to + 1, to + 11) <*
              logger.info(s"Current batches number is $size. Need 100. Going to request more boxes")
    } yield ()

  private def collectBatches(boxes: List[AssetBox]): F[List[BoxesBatch]] =
    Sync[F].delay(
      boxes
        .foldLeft(List.empty[BoxesBatch], BoxesBatch.empty) {
          case ((batches, thisBatch), nextBox) =>
            val newBatchBoxes: List[AssetBox] = nextBox :: thisBatch.boxes
            val nexBatchBoxesAmount: Long     = newBatchBoxes.foldLeft(0L)(_ |+| _.amount)
            if (nexBatchBoxesAmount >= 1000000) (BoxesBatch(newBatchBoxes) :: batches, BoxesBatch.empty)
            else (batches, BoxesBatch(newBatchBoxes))
        }
        ._1
    )

}

object TransactionGeneratorClient {
  def init[F[_]: Async: Timer: ContextShift: Parallel](
    client: Client[F],
    logger: Logger[F],
    storage: BatchesStorage[F],
    contractHashStorage: ContractHashStorage[F]
  )(implicit ec: ExecutionContext): F[TransactionGeneratorClient[F]] =
    Sync[F].pure(new TransactionGeneratorClient[F](client, logger, storage, contractHashStorage))
}
