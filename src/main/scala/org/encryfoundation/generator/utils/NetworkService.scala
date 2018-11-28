package org.encryfoundation.generator.utils

import java.net.InetSocketAddress
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.util.ByteString
import io.circe.parser.decode
import io.circe.syntax._
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.transaction.box.Box
import org.encryfoundation.generator.GeneratorApp._
import scala.concurrent.Future

object NetworkService {

  def requestUtxos(host: InetSocketAddress): Future[Seq[Box]] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = "/wallet/utxos"
    ).withEffectiveUri(securedConnection = false, Host(host)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[Seq[Box]])
      .flatMap(_.fold(Future.failed, Future.successful))

  def commitTransaction(node: Node, txs: EncryTransaction): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = "/transactions/send",
      entity = HttpEntity(ContentTypes.`application/json`, txs.asJson.toString)
    ).withEffectiveUri(securedConnection = false, Host(node.host, node.port)))
}