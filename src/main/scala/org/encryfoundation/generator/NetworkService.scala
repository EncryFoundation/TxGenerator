package org.encryfoundation.generator

import java.net.InetSocketAddress
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.stream.Materializer
import akka.util.ByteString
import io.circe.parser.decode
import io.circe.syntax._
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.transaction.box.Box
import scala.concurrent.{ExecutionContext, Future}

case class NetworkService(host: InetSocketAddress)(implicit val system: ActorSystem,
                                                   implicit val materializer: Materializer,
                                                   implicit val ec: ExecutionContext) {

  def requestUtxos: Future[Seq[Box]] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = "/wallet/utxos"
    ).withEffectiveUri(securedConnection = false, Host(host)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[Seq[Box]])
      .flatMap(_.fold(Future.failed, Future.successful))

  def commitTransaction(tx: EncryTransaction): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = "/transactions/send",
      entity = HttpEntity(ContentTypes.`application/json`, tx.asJson.toString)
    ).withEffectiveUri(securedConnection = false, Host(host)))

}
