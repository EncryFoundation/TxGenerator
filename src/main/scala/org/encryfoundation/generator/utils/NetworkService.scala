package org.encryfoundation.generator.utils

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import io.circe.syntax._
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.GeneratorApp._
import scala.concurrent.Future

object NetworkService {

  def commitTransaction(node: Node, txs: EncryTransaction): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = "/transactions/send",
      entity = HttpEntity(ContentTypes.`application/json`, txs.asJson.toString)
    ).withEffectiveUri(securedConnection = false, Host(node.host, node.port)))
}