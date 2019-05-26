package org.encryfoundation.generator.utils

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, HCursor}
import io.circe.syntax._
import io.circe.parser.decode
import org.encryfoundation.common.Algos
import org.encryfoundation.common.transaction.PubKeyLockedContract
import org.encryfoundation.generator.modifiers.Transaction
import org.encryfoundation.generator.GeneratorApp._
import org.encryfoundation.generator.modifiers.box.Box
import scala.concurrent.Future
import scala.util.control.NonFatal

object NetworkService extends StrictLogging {

  def commitTransaction(node: NetworkSettings, tx: Transaction): Future[HttpResponse] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = "/transactions/send",
      entity = HttpEntity(ContentTypes.`application/json`, tx.asJson.toString)
    ).withEffectiveUri(securedConnection = false, Host(node.peerForConnectionHost, node.peerForConnectionPort)))
      .recoverWith {
        case NonFatal(th) =>
          th.printStackTrace()
          Future.failed(th)
      }

  def requestUtxos(node: Node, from: Int, to: Int): Future[List[Box]] = {
    val privKey = Mnemonic.createPrivKey(Option(node.mnemonicKey))
    val contractHash: String = Algos.encode(PubKeyLockedContract(privKey.publicImage.pubKeyBytes).contract.hash)
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = s"/wallet/$contractHash/boxes/$from/$to"
    ).withEffectiveUri(securedConnection = false, Host(node.explorerHost, node.explorerPort)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[List[Box]])
      .flatMap(_.fold(Future.failed, Future.successful))
  }

  def checkTxsInBlockchain(node: NetworkSettings, txsToCheck: Vector[String], numberOfBlocks: Int): Future[List[String]] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = s"/history/lastHeaders/$numberOfBlocks"
    ).withEffectiveUri(securedConnection = false, Host(node.peerForConnectionHost, node.peerForConnectionPort)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[List[HeaderId]])
      .flatMap(_.fold(Future.failed, Future.successful))
      .flatMap { headers =>
        Future.sequence(headers.map(checkTxsInBlock(node, txsToCheck, _))).map(_.flatten)
      }
      .flatMap { txs =>
        if (txs.isEmpty) Future.failed(new RuntimeException) else Future.successful(txs)
      }
      .recover {
        case NonFatal(_) => List.empty
      }

  private def checkTxsInBlock(node: NetworkSettings, txsToCheck: Vector[String], headerId: HeaderId): Future[List[String]] =
    Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = s"/history/${headerId.id}/transactions"
    ).withEffectiveUri(securedConnection = false, Host(node.peerForConnectionHost, node.peerForConnectionPort)))
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
      .map(decode[List[TransactionId]])
      .flatMap(_.fold(Future.failed, Future.successful))
      .map(_.map(_.id).intersect(txsToCheck))
      .map { txs =>
        if (txs.nonEmpty) logger.info(s"txs ${txs.mkString(",")} are in a block ${headerId.id}")
        txs
      }.recoverWith {
      case NonFatal(th) =>
        th.printStackTrace()
        Future.failed(th)
    }

  case class HeaderId(id: String)
  case class TransactionId(id: String)

  implicit val headerIdDecoder: Decoder[HeaderId] = (c: HCursor) =>
    for { id <- c.downField("id").as[String] } yield HeaderId(id)
  implicit val transactionIdDecoder: Decoder[TransactionId] = (c: HCursor) =>
    for { id <- c.downField("id").as[String] } yield TransactionId(id)
}