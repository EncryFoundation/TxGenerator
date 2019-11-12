package org.encryfoundation.generator.processors

import cats.Parallel
import cats.effect.{ Async, Sync, Timer }
import cats.instances.list._
import cats.instances.long._
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.syntax.semigroup._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import org.encryfoundation.common.modifiers.state.box.{ AssetBox, MonetaryBox }
import org.encryfoundation.generator.actors.BoxesBatch
import org.encryfoundation.generator.storage.BatchesStorage.{ BatchType, MonetaryBatch }
import org.encryfoundation.generator.storage.{ BatchesStorage, ContractHashStorage }
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.client.Client
import io.circe.generic.auto._
import scala.collection.immutable.HashMap
import scala.concurrent.duration._

final class HttpApiBoxesProcessor[F[_]: Async: Timer: Parallel](
  client: Client[F],
  logger: Logger[F],
  batchesStorage: BatchesStorage[F],
  contractHashStorage: ContractHashStorage[F]
) {

  def run: Stream[F, Unit] =
    Stream(()).repeat
      .covary[F]
      .metered(10.seconds)
      .evalMap[F, Unit](_ => requestNewBoxesForAllKeys)

  private def requestNewBoxesForAllKeys: F[Unit] =
    for {
      addresses <- contractHashStorage.getAllElements
      _         <- logger.info(s"Start processing new boxes from api. Addresses storage size is ${addresses.size}")
      _         <- addresses.map(address => requestNUtxos(address, 0, 10)).parSequence
    } yield ()

  private def requestUtxoApi(contractHash: String, from: Int, to: Int): F[List[MonetaryBox]] =
    client
      .expect[List[MonetaryBox]](
        Uri.unsafeFromString(s"http://172.16.10.58:9000/wallet/$contractHash/boxes/$from/$to")
      )(jsonOf[F, List[MonetaryBox]])
      .handleErrorWith { f: Throwable =>
        logger.error(s"While request utxos error ${f.getMessage} has occurred") *> Sync[F].pure(List.empty)
      } <* logger.info(s"Received new boxes from http api!")

  private def requestNUtxos(contractHash: String, from: Int, to: Int): F[Unit] =
    for {
      _       <- logger.info(s"Start requesting boxes for $contractHash")
      boxes   <- requestUtxoApi(contractHash, from, to)
      batches <- collectBatches(boxes)
      _ <- Sync[F].delay(batches.map {
            case (batchType, batches) =>
              batchesStorage.insert(contractHash, batchType, batches)
          })
      size <- batchesStorage.getSizeByKey(contractHash, MonetaryBatch)
      _ <- if (size > 100 || boxes.isEmpty)
            Sync[F].unit <* logger.info(
              s"Current number of boxes is $size. Received boxes size is ${boxes.size} Stop requesting new boxes"
            )
          else
            requestNUtxos(contractHash, to + 1, to + 11) <*
              logger.info(
                s"Current batches number for key $contractHash is $size. Need 100. Going to request more boxes"
              )
    } yield ()

  private def collectBatches(boxes: List[MonetaryBox]): F[HashMap[BatchType, List[BoxesBatch]]] =
    Sync[F].pure(
      boxes
        .foldLeft(HashMap.empty[BatchType, List[BoxesBatch]], BoxesBatch.empty) {
          case ((storage, batch), box: AssetBox) if box.tokenIdOpt.isEmpty =>
            val newBatchBoxes: List[MonetaryBox] = box :: batch.boxes
            val nexBatchBoxesAmount: Long        = newBatchBoxes.foldLeft(0L)(_ |+| _.amount)
            if (nexBatchBoxesAmount >= 100000) {
              val currentBatches: List[BoxesBatch] = storage.getOrElse(MonetaryBatch, List.empty)
              val updatedBatches: List[BoxesBatch] = BoxesBatch(newBatchBoxes) :: currentBatches
              storage.updated(MonetaryBatch, updatedBatches) -> BoxesBatch.empty
            } else (storage, BoxesBatch(newBatchBoxes))
          case ((storage, batch), _) => (storage, batch) //todo implement later
        }
        ._1
    )
}

object HttpApiBoxesProcessor {
  def apply[F[_]: Async: Timer: Parallel](
    client: Client[F],
    logger: Logger[F],
    storage: BatchesStorage[F],
    contractHashStorage: ContractHashStorage[F]
  ): F[HttpApiBoxesProcessor[F]] =
    Sync[F].pure(new HttpApiBoxesProcessor[F](client, logger, storage, contractHashStorage))
}
