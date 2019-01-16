package org.encryfoundation.generator.utils

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.util.ByteString
import io.circe.syntax._
import io.circe.parser.decode
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.GeneratorApp._
import org.encryfoundation.generator.transaction.box.Box
import scala.concurrent.Future

object NetworkService {

  def commitTransaction(node: Node, tx: EncryTransaction): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = "/transactions/send",
      entity = HttpEntity(ContentTypes.`application/json`, tx.asJson.toString)
    ).withEffectiveUri(securedConnection = false, Host(node.host, node.port)))

  def requestUtxos(node: Node): Future[List[Box]] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = "/wallet/utxos"
    ).withEffectiveUri(securedConnection = false, Host(node.host, node.port)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[List[Box]])
      .flatMap(_.fold(Future.failed, Future.successful))
}