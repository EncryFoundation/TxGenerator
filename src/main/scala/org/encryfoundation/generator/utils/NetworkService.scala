package org.encryfoundation.generator.utils

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.util.ByteString
import akka.util.ByteString
import io.circe.{Decoder, HCursor, Json}
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.parser._
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.GeneratorApp._
import org.encryfoundation.generator.transaction.box.Box

import scala.concurrent.Future
import scala.util.control.NonFatal

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

  def checkTxsInBlockchain(node: Node, txsToCheck: Vector[String], numberOfBlocks: Int): Future[List[String]] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = s"/history/lastHeaders/$numberOfBlocks"
    ).withEffectiveUri(securedConnection = false, Host(node.host, node.port)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[List[HeaderId]])
      .flatMap(_.fold(Future.failed, Future.successful))
      .flatMap { headers =>
        Future.sequence(headers.map(checkTxsInBlock(node, txsToCheck, _))).map(_.flatten)
      }
      .recover {
        case NonFatal(_) => List.empty
      }

  private def checkTxsInBlock(node: Node, txsToCheck: Vector[String], headerId: HeaderId): Future[List[String]] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = s"/history/${headerId.id}/transactions"
    ).withEffectiveUri(securedConnection = false, Host(node.host, node.port)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[List[TransactionId]])
      .flatMap(_.fold(Future.failed, Future.successful))
      .map(_.map(_.id).intersect(txsToCheck))
      .recover {
        case NonFatal(_) => List.empty
      }

  case class HeaderId(id: String)
  case class TransactionId(id: String)

  implicit val headerIdDecoder: Decoder[HeaderId] = (c: HCursor) =>
    for { id <- c.downField("id").as[String] } yield HeaderId(id)
  implicit val transactionIdDecoder: Decoder[TransactionId] = (c: HCursor) =>
    for { id <- c.downField("id").as[String] } yield TransactionId(id)
}