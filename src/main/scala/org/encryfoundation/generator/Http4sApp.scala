package org.encryfoundation.generator

import cats.effect.{ ExitCode, IO, IOApp }
import fs2.Stream
import cats.syntax.functor._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.modifiers.mempool.transaction.PubKeyLockedContract
import org.encryfoundation.common.utils.Algos
import org.encryfoundation.generator.http.api.TransactionGeneratorClient
import org.http4s.client.blaze.BlazeClientBuilder
import org.encryfoundation.generator.storage.{ BatchesStorage, ContractHashStorage }
import org.encryfoundation.generator.utils.Mnemonic
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import cats.syntax.apply._

object Http4sApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    val privateKey: PrivateKey25519 = Mnemonic.createPrivKey(
      Some("boat culture ribbon wagon deposit decrease maid speak equal thunder have beauty")
    )
    val contractHash: String = Algos.encode(PubKeyLockedContract(privateKey.publicImage.pubKeyBytes).contract.hash)

    val streamF: Stream[IO, Unit] = for {
      logger         <- Stream.eval(Slf4jLogger.create[IO])
      batchesStorage <- Stream.eval(BatchesStorage.apply[IO](logger))
      keysStorage    <- Stream.eval(ContractHashStorage.apply[IO](logger))
      _              <- Stream.eval(keysStorage.init)
      service        <- Stream.resource(BlazeClientBuilder[IO](global).resource)
      client         <- Stream.eval(TransactionGeneratorClient.init[IO](service, logger, batchesStorage, keysStorage))
      _ <- client.requestNewBoxes concurrently Stream(()).repeat.covary[IO].metered(1.seconds).evalMap { _ =>
            for {
              _    <- batchesStorage.getMany(contractHash, 5)
              size <- batchesStorage.getSizeByKey(contractHash)
              _    <- logger.info(s"Got 5 boxes from current storage. Current value is $size")
            } yield ()
          }
      _ <- Stream.eval(logger.info(s"Started boxes asker"))
    } yield ()
    streamF.compile.drain.as(ExitCode.Success)
  }
}
