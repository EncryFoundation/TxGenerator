package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.transaction.PubKeyLockedContract
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.transaction.{EncryTransaction, Transaction}
import org.encryfoundation.generator.transaction.box.AssetBox
import scala.concurrent.ExecutionContext.Implicits.global
import org.encryfoundation.generator.utils.{NetworkService, Node, Settings}
import scorex.utils
import scala.concurrent.Future
import scala.concurrent.duration._

class Generator(settings: Settings,
                privKey: PrivateKey25519,
                nodeForLocalPrivKey: Node) extends Actor with StrictLogging {

  val influx: Option[ActorRef] =
    settings.influxDB.map(_ => context.actorOf(InfluxActor.props(settings), "influxDB"))
  val boxesHolder: ActorRef = context.system.actorOf(
      BoxesHolder.props(settings, influx, nodeForLocalPrivKey), s"boxesHolder${nodeForLocalPrivKey.host}")
  context.system.scheduler.schedule(10.seconds, settings.generator.askBoxesHolderForBoxesPeriod.seconds) {
    boxesHolder ! AskBoxesFromGenerator
    logger.info(s"Generator asked boxesHolder for new boxes.")
  }

  override def receive: Receive = {
    case BoxesForGenerator(boxes, txType) if boxes.nonEmpty =>
      generateAndSendTransaction(boxes, txType)
    case _ =>
  }

  def generateAndSendTransaction(boxes: List[AssetBox], txsType: Int): Future[Unit] = Future {
    val transaction: EncryTransaction = txsType match {
      case 1 => Transaction.dataTransactionScratch(
        privKey,
        settings.transactions.feeAmount,
        System.currentTimeMillis(),
        boxes.map(_ -> None),
        PubKeyLockedContract(privKey.publicImage.pubKeyBytes).contract,
        settings.transactions.requiredAmount - settings.transactions.feeAmount,
        utils.Random.randomBytes(settings.transactions.dataTxSize),
        settings.transactions.numberOfCreatedDirectives
      )
      case 2 => Transaction.defaultPaymentTransaction(
        privKey,
        settings.transactions.feeAmount,
        System.currentTimeMillis(),
        boxes.map(_ -> None),
        privKey.publicImage.address.address,
        settings.transactions.requiredAmount - settings.transactions.feeAmount,
        settings.transactions.numberOfCreatedDirectives
      )
    }
    settings.peers.foreach(NetworkService.commitTransaction(_, transaction))
    logger.info(s"Generated and sent new transaction with id: ${Algos.encode(transaction.id)}." +
      s" Tx type is: ${txsType match {
        case 1 => "DataTx"
        case 2 => "MonetaryTx"
      }}")
  }
}

object Generator {
  def props(settings: Settings, privKey: PrivateKey25519, nodeForLocalPrivKey: Node): Props =
    Props(new Generator(settings, privKey, nodeForLocalPrivKey))
}