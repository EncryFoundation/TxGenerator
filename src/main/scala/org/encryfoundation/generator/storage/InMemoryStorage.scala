package org.encryfoundation.generator.storage

trait InMemoryStorage[F[_], T] {
  def insert(elem: T): F[Unit]
  def clean: F[Unit]
}
