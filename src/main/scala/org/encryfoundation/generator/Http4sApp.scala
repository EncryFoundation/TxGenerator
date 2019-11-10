package org.encryfoundation.generator

import cats.effect.{ ExitCode, IO, IOApp }
import fs2.Stream
import cats.syntax.functor._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.encryfoundation.generator.http.api.TransactionGeneratorClient
import org.http4s.client.blaze.BlazeClientBuilder
import org.encryfoundation.generator.storage.{ BatchesStorage, ContractHashStorage }
import scala.concurrent.ExecutionContext.Implicits.global

object Http4sApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    val streamF: Stream[IO, Unit] = for {
      logger         <- Stream.eval(Slf4jLogger.create[IO])
      _              <- Stream.eval(logger.info("Start app. For iteration"))
      batchesStorage <- Stream.eval(BatchesStorage.apply[IO](logger))
      keysStorage    <- Stream.eval(ContractHashStorage.apply[IO](logger))
      boxesProcessor <- Stream.eval(BoxesProcessor.apply[IO](logger, batchesStorage, keysStorage))
      _              <- Stream.eval(keysStorage.init)
      service        <- Stream.resource(BlazeClientBuilder[IO](global).resource)
      client         <- Stream.eval(TransactionGeneratorClient.init[IO](service, logger, batchesStorage, keysStorage))
      _              <- client.run concurrently boxesProcessor.run
    } yield ()
    streamF.compile.drain.as(ExitCode.Success)
  }
}
