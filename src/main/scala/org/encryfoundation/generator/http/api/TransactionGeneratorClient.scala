package org.encryfoundation.generator.http.api

import cats.{ Functor, Monad }
import cats.effect.{ Async, Sync, Timer }
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
import org.encryfoundation.generator.actors.BoxesBatch
import cats.instances.long._
import cats.instances.list._
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.modifiers.mempool.transaction.PubKeyLockedContract
import org.encryfoundation.common.utils.Algos
import org.encryfoundation.generator.utils.Mnemonic

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

final class TransactionGeneratorClient[F[_]: Async: Monad: Timer](
  client: Client[F],
  logger: Logger[F],
  batchesStorage: BatchesStorage[F],
  contractHashStorage: ContractHashStorage[F]
)(implicit ec: ExecutionContext) {

  val privateKey: PrivateKey25519 = Mnemonic.createPrivKey(
    Some("boat culture ribbon wagon deposit decrease maid speak equal thunder have beauty")
  )
  val contractHash: String = Algos.encode(PubKeyLockedContract(privateKey.publicImage.pubKeyBytes).contract.hash)

  def requestNewBoxes: Stream[F, Unit] =
    for {
      keys <- Stream.eval(contractHashStorage.getAllKeys)
      _    <- Stream.eval(logger.info(s"requestNewBoxes started. Keys size ${keys.size}"))
      _ <- Stream(()).repeat.covary[F].metered(5.seconds).evalMap[F, Unit] { _ =>
            for {
              _ <- logger.info(s"Start request")
              _ <- requestNUtxos(contractHash, from = 0, to = 10)
              _ <- logger.info("Finish boxes request")
            } yield ()
//            Functor[F]
//              .compose[List]
//              .map(contractHashStorage.getAllKeys)(key => requestNUtxos(key, from = 0, to = 10))
//              .map(_ => ())
          }
    } yield ()
  private def requestUtxoApi(contractHash: String, from: Int, to: Int): F[List[AssetBox]] =
    client
      .expect[List[AssetBox]](
        Uri.unsafeFromString(s"http://172.16.10.58:9000/wallet/$contractHash/boxes/$from/$to")
      )(jsonOf[F, List[AssetBox]])
      .handleErrorWith { f: Throwable =>
        logger.error(f)("While request utxos error has occurred") *> Sync[F].pure(List.empty)
      } <* logger.info(s"Handled new boxes from http api!")

  private def requestUtxoApiAsync(contractHash: String, from: Int, to: Int): F[List[AssetBox]] =
    Async[F].async { _ =>
      ec.execute { () =>
        client
          .expect[List[AssetBox]](
            Uri.unsafeFromString(s"http://172.16.10.58:9000/wallet/$contractHash/boxes/$from/$to")
          )(jsonOf[F, List[AssetBox]])
          .handleErrorWith { f: Throwable =>
            logger.error(f)("While request utxos error has occurred") *> Sync[F].pure(List.empty)
          } <* logger.info(s"Handled new boxes from http api!")
      }
    }

  private def requestNUtxos(contractHash: String, from: Int, to: Int): F[Unit] =
    for {
      _       <- logger.info(s"Request boxes for $contractHash")
      boxes   <- requestUtxoApi(contractHash, from, to)
      batches <- collectBatches(boxes)
      _       <- batchesStorage.insertMany(contractHash, batches)
      size    <- batchesStorage.getSizeByKey(contractHash)
      _ <- if (size > 100) Sync[F].unit <* logger.info(s"Current number of boxes is $size. Stop requesting new boxes")
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
  def init[F[_]: Sync: Async: Timer](
    client: Client[F],
    logger: Logger[F],
    storage: BatchesStorage[F],
    contractHashStorage: ContractHashStorage[F]
  )(implicit ec: ExecutionContext): F[TransactionGeneratorClient[F]] =
    Sync[F].pure(new TransactionGeneratorClient[F](client, logger, storage, contractHashStorage))
}
