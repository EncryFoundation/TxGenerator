package org.encryfoundation.generator.storage

import cats.Functor
import cats.syntax.functor._
import cats.effect.Sync
import cats.effect.concurrent.Ref
import com.google.common.base.Charsets
import com.google.common.hash.{BloomFilter, Funnels}

final class ProcessedBoxesIdsStorage[F[_] : Functor] private(
  ref: Ref[F, BloomFilter[String]]
)
  //extends InMemoryStorage[F, String]
{
//
//  def insert(elem: String): F[Unit] = ref.update { filter =>
//    filter.put(elem)
//    filter
//  }
//
//  def clean: F[Unit] = ref.set(ProcessedBoxesIdsStorage.initFilter)
//
//  def ifContains(elem: String): F[Boolean] = ref.get.map(_.mightContain(elem))
}

object ProcessedBoxesIdsStorage {
//  def apply[F[_]: Sync: Functor]: F[ProcessedBoxesIdsStorage[F]] =
//    Ref[F]
//      .of(initFilter)
//      .map(new ProcessedBoxesIdsStorage(_))
//
//  def initFilter: BloomFilter[String] = BloomFilter.create(
//    Funnels.stringFunnel(Charsets.UTF_8),
//    10000,
//    Double.MinPositiveValue
//  )
}
