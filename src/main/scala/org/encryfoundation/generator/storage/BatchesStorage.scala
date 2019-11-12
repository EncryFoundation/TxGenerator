package org.encryfoundation.generator.storage

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import cats.syntax.apply._
import org.encryfoundation.generator.actors.BoxesBatch
import io.chrisdavenport.log4cats.Logger
import org.encryfoundation.generator.storage.BatchesStorage.BatchType
import scala.collection.immutable.HashMap

final class BatchesStorage[F[_]: Sync] private (
  ref: Ref[F, HashMap[String, HashMap[BatchType, List[BoxesBatch]]]],
  logger: Logger[F]
) {

  def insert(contractHash: String, batchType: BatchType, batches: List[BoxesBatch]): F[Unit] =
    ref.update { storage: HashMap[String, HashMap[BatchType, List[BoxesBatch]]] =>
      val existBatches: HashMap[BatchType, List[BoxesBatch]] = storage.getOrElse(contractHash, HashMap.empty)
      val newBatches: List[BoxesBatch] = existBatches
        .get(batchType)
        .map(batches ::: _)
        .getOrElse(batches)
      val updatedBatches: HashMap[BatchType, List[BoxesBatch]] = existBatches.updated(batchType, newBatches)
      storage.updated(contractHash, updatedBatches)
    } *> logger.info(
      s"Batch for key $contractHash with type $batchType and ${batches.size} batches inserted into storage"
    )

  def getMany(contractHash: String, batchType: BatchType, quantity: Int): F[List[BoxesBatch]] =
    ref.modify { storage: HashMap[String, HashMap[BatchType, List[BoxesBatch]]] =>
      val existBatches: HashMap[BatchType, List[BoxesBatch]] = storage.getOrElse(contractHash, HashMap.empty)
      val batchesByType: List[BoxesBatch]                    = existBatches.getOrElse(batchType, List.empty)
      val response: List[BoxesBatch]                         = batchesByType.take(quantity)
      val updatedBatches: HashMap[BatchType, List[BoxesBatch]] =
        existBatches.updated(batchType, batchesByType.drop(quantity))
      val newStorage: HashMap[String, HashMap[BatchType, List[BoxesBatch]]] =
        storage.updated(contractHash, updatedBatches)
      newStorage -> response
    } <* logger.info(s"Took $quantity batches of type $batchType from storage")

  def getSizeByKey(contractHash: String, batchType: BatchType): F[Int] =
    ref.get.map(_.get(contractHash).map(_.get(batchType).map(_.size)).getOrElse(0))

  def clean: F[Unit] =
    ref.set(HashMap.empty[String, HashMap[BatchType, List[BoxesBatch]]]) <*
      logger.info(s"Batches storage cleaned up")
}

object BatchesStorage {
  def apply[F[_]: Sync](logger: Logger[F]): F[BatchesStorage[F]] =
    Ref[F]
      .of(HashMap.empty[String, HashMap[BatchType, List[BoxesBatch]]])
      .map(ref => new BatchesStorage(ref, logger))

  sealed trait BatchType
  case object MonetaryBatch extends BatchType
  case object AssetBatch    extends BatchType
}
