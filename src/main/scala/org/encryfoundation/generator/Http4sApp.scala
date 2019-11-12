package org.encryfoundation.generator

import cats.effect.{ ExitCode, IO, IOApp }
import fs2.Stream
import cats.syntax.functor._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.encryfoundation.generator.processors.{ HttpApiBoxesProcessor, TransactionGenerator }
import org.http4s.client.blaze.BlazeClientBuilder
import org.encryfoundation.generator.storage.{ BatchesStorage, ContractHashStorage, TransactionsStorage }
import scala.concurrent.ExecutionContext.Implicits.global

object Http4sApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    val streamF: Stream[IO, Unit] = for {
      logger              <- Stream.eval(Slf4jLogger.create[IO])
      _                   <- Stream.eval(logger.info("Generator app started"))
      service             <- Stream.resource(BlazeClientBuilder[IO](global).resource)
      batchesStorage      <- Stream.eval(BatchesStorage.apply[IO](logger))
      keysStorage         <- Stream.eval(ContractHashStorage.apply[IO](logger))
      transactionsStorage <- Stream.eval(TransactionsStorage.apply[IO])
      boxesProcessor <- Stream.eval(
                         TransactionGenerator.apply[IO](logger, batchesStorage, keysStorage, transactionsStorage)
                       )
      _          <- Stream.eval(keysStorage.init)
      httpClient <- Stream.eval(HttpApiBoxesProcessor.apply[IO](service, logger, batchesStorage, keysStorage))
      _          <- httpClient.run concurrently boxesProcessor.run
    } yield ()
    streamF.compile.drain.as(ExitCode.Success)
  }
}
