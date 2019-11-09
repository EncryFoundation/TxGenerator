package org.encryfoundation.generator

import cats.Monad
import cats.effect.Timer
import io.chrisdavenport.log4cats.Logger
import org.encryfoundation.generator.http.api.TransactionGeneratorClient
import org.encryfoundation.generator.storage.BatchesStorage

final class BoxesProcessor[F[_]: Timer : Monad] private(
  logger: Logger[F],
  httpClient: TransactionGeneratorClient[F],
  storage: BatchesStorage[F]
) {
//  val loopRequestHttpApi: F[Unit] = for {
//    batches <- httpClient.requestUtxos
//  } yield loopRequestHttpApi
}

object BoxesProcessor {}
