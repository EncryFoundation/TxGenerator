package org.encryfoundation.generator.utils

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._
import io.circe.parser.decode
import org.encryfoundation.common.Algos
import org.encryfoundation.common.transaction.PubKeyLockedContract
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.GeneratorApp._
import org.encryfoundation.generator.transaction.box.Box

import scala.concurrent.Future

object NetworkService extends StrictLogging{

  def commitTransaction(node: Node, tx: EncryTransaction): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = "/transactions/send",
      entity = HttpEntity(ContentTypes.`application/json`, tx.asJson.toString)
    ).withEffectiveUri(securedConnection = false, Host(node.nodeHost, node.nodePort)))

  def requestUtxos(node: Node): Future[List[Box]] = {
    val privKey = Mnemonic.createPrivKey(Option(node.mnemonicKey))
    val contractHash = Algos.encode(PubKeyLockedContract(privKey.publicImage.pubKeyBytes).contract.hash)
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = s"/wallet/$contractHash/boxes/0/100"
    ).withEffectiveUri(securedConnection = false, Host(node.explorerHost, node.explorerPort)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[List[Box]])
      .flatMap(_.fold(Future.failed, Future.successful))
  }
}