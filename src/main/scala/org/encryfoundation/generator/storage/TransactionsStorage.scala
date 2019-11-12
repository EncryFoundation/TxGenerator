package org.encryfoundation.generator.storage

import cats.syntax.functor._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import org.encryfoundation.common.modifiers.mempool.transaction.Transaction
import scala.collection.immutable.HashMap

final class TransactionsStorage[F[_]: Sync](
  ref: Ref[F, HashMap[String, Transaction]]
) extends InMemoryStorage[F, (String, Transaction)] {
  def insert(elem: (String, Transaction)): F[Unit] = ref.update(_.updated(elem._1, elem._2))

  def clean: F[Unit] = ref.set(HashMap.empty[String, Transaction])

  def get(key: String): F[Option[Transaction]] = ref.modify(storage => storage - key -> storage.get(key))
}

object TransactionsStorage {
  def apply[F[_]: Sync]: F[TransactionsStorage[F]] =
    Ref[F]
      .of(HashMap.empty[String, Transaction])
      .map(new TransactionsStorage(_))
}
