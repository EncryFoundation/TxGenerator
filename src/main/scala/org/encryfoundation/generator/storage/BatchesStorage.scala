package org.encryfoundation.generator.storage

import cats.effect.Sync
import cats.effect.concurrent.Ref
import org.encryfoundation.generator.actors.BoxesBatch
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import cats.syntax.apply._
import scala.collection.immutable.HashMap
import cats.instances.list._
import cats.syntax.semigroup._

final class BatchesStorage[F[_]: Sync] private (
  ref: Ref[F, HashMap[String, List[BoxesBatch]]],
  logger: Logger[F]
) extends InMemoryStorage[F, BoxesBatch] {

  @deprecated
  def insert(elem: BoxesBatch): F[Unit] = insert("", elem)

  def insert(key: String, batch: BoxesBatch): F[Unit] =
    ref.update { storage =>
      val updatedBatches = storage.get(key).map(batch :: _).getOrElse(List(batch))
      storage.updated(key, updatedBatches)
    } *> logger.info(s"Batch for key $key with elems ${batch.boxes.size} inserted into storage")

  def insertMany(key: String, batches: List[BoxesBatch]): F[Unit] =
    ref.update { storage =>
      val currentBatches: List[BoxesBatch] = storage.getOrElse(key, List.empty)
      val newBatches: List[BoxesBatch]     = batches |+| currentBatches
      storage.updated(key, newBatches)
    } *> logger.info(s"${batches.size} for key $key inserted into storage")

  def getMany(key: String, number: Int): F[List[BoxesBatch]] =
    ref.modify { storage =>
      val batches: List[BoxesBatch]                   = storage.getOrElse(key, List.empty)
      val response: List[BoxesBatch]                  = batches.take(number)
      val newValue: HashMap[String, List[BoxesBatch]] = storage.updated(key, batches.drop(number))
      newValue -> response
    } <* logger.info(s"Got $number batches from storage")

  def getSizeByKey(key: String): F[Int] = ref.get.map(_.get(key).map(_.size).getOrElse(0))

  def showSize: F[Unit] = ref.get.map(l => println(l.size))

  def clean: F[Unit] = ref.set(HashMap.empty[String, List[BoxesBatch]]) <* logger.info(s"Batches storage cleaned up")
}

object BatchesStorage {
  def apply[F[_]: Sync](logger: Logger[F]): F[BatchesStorage[F]] =
    Ref[F]
      .of(HashMap.empty[String, List[BoxesBatch]])
      .map(ref => new BatchesStorage(ref, logger))
}
