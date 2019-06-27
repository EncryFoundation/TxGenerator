package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.modifiers.mempool.transaction.PubKeyLockedContract
import org.encryfoundation.common.utils.Algos
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.actors.Generator.TransactionForCommit
import org.encryfoundation.generator.modifiers.{Transaction, TransactionsFactory}
import org.encryfoundation.generator.modifiers.box.AssetBox
import scala.concurrent.ExecutionContext.Implicits.global
import org.encryfoundation.generator.utils.{PeerForConnection, Settings}
import scorex.utils
import scala.concurrent.Future
import scala.concurrent.duration._

class Generator(settings: Settings,
                privKey: PrivateKey25519,
                nodeForLocalPrivKey: PeerForConnection,
                influx: Option[ActorRef],
                networkServer: ActorRef) extends Actor with StrictLogging {

  val boxesHolder: ActorRef = context.system.actorOf(
      BoxesHolder.props(settings, influx, nodeForLocalPrivKey), s"boxesHolder${nodeForLocalPrivKey.explorerHost}")
  context.system.scheduler.schedule(10.seconds, settings.generator.transactionsSendingFrequency.seconds) {
    boxesHolder ! AskBoxesFromGenerator
    logger.info(s"Generator asked boxesHolder for new boxes.")
  }

  override def receive: Receive = {
    case BoxesForGenerator(boxes, txType) if boxes.nonEmpty =>
      generateAndSendTransaction(boxes, txType)
    case _ =>
  }

  def generateAndSendTransaction(boxes: List[AssetBox], txsType: Int): Future[Unit] = Future {
    val transaction: Transaction = txsType match {
      case 1 => TransactionsFactory.dataTransactionScratch(
        privKey,
        settings.transactions.feeAmount,
        System.currentTimeMillis(),
        boxes.map(_ -> None),
        PubKeyLockedContract(privKey.publicImage.pubKeyBytes).contract,
        settings.transactions.requiredAmount,
        utils.Random.randomBytes(settings.transactions.dataTxSize),
        settings.transactions.numberOfCreatedDirectives
      )
      case 2 => TransactionsFactory.defaultPaymentTransaction(
        privKey,
        settings.transactions.feeAmount,
        System.currentTimeMillis(),
        boxes.map(_ -> None),
        privKey.publicImage.address.address,
        settings.transactions.requiredAmount,
        settings.transactions.numberOfCreatedDirectives
      )
    }
    logger.info(s"Commit tx ${Algos.encode(transaction.id)} with type: ${txsType match {
      case 1 => "DataTx"
      case 2 => "MonetaryTx"
    }}")
    networkServer ! TransactionForCommit(transaction)
  }
}

object Generator {

  case class TransactionForCommit(tx: Transaction)

  def props(settings: Settings,
            privKey: PrivateKey25519,
            nodeForLocalPrivKey: PeerForConnection,
            influx: Option[ActorRef],
            networkServer: ActorRef): Props =
    Props(new Generator(settings, privKey, nodeForLocalPrivKey, influx, networkServer))
}