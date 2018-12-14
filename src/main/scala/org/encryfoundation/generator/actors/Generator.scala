package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.transaction.PubKeyLockedContract
import org.encryfoundation.generator.actors.BoxesHolder.{AskBoxesFromGenerator, BoxesAnswerToGenerator, ReturnedBoxes}
import org.encryfoundation.generator.transaction.{EncryTransaction, Transaction}
import org.encryfoundation.generator.transaction.box.MonetaryBox
import scala.concurrent.ExecutionContext.Implicits.global
import org.encryfoundation.generator.utils.{NetworkService, Settings}
import org.encryfoundation.generator.wallet.WalletStorageReader
import scorex.utils
import scala.concurrent.Future
import scala.concurrent.duration._

class Generator(settings: Settings,
                privKey: PrivateKey25519,
                walletStorageReader: WalletStorageReader) extends Actor with StrictLogging {

  val boxesHolder: ActorRef =
    context.system.actorOf(BoxesHolder.props(settings, walletStorageReader), "boxesHolder")
  context.system.scheduler.schedule(10.seconds, settings.generator.askBoxesHolderForBoxesPeriod.seconds) {
    boxesHolder ! AskBoxesFromGenerator
    logger.info(s"Generator asked boxesHolder for new boxes.")
  }

  override def receive: Receive = {
    case BoxesAnswerToGenerator(boxes) if boxes.nonEmpty =>
      val lastBoxesAfterGeneratingDataTxs: List[MonetaryBox] = generateNTransactions(
        settings.transactions.numberOfDataTxs,
        boxes,
        settings.transactions.dataTx
      )
      val lastBoxesAfterGenerationPaymentTxs: List[MonetaryBox] = generateNTransactions(
        settings.transactions.totalNumberOfTxs - settings.transactions.numberOfDataTxs,
        lastBoxesAfterGeneratingDataTxs,
        settings.transactions.paymentTx
      )
      boxesHolder ! ReturnedBoxes(lastBoxesAfterGenerationPaymentTxs)
    case _ => logger.info(s"No boxes in IoDB.")
  }

  def generateNTransactions(numberOfTxs: Int, monetaryBoxes: List[MonetaryBox], txsType: String): List[MonetaryBox] =
    (0 until numberOfTxs).foldLeft(monetaryBoxes) {
      case (innerBoxes, number) if innerBoxes.nonEmpty =>
        val boxesForTx: (List[MonetaryBox], Long) = innerBoxes.foldLeft(List[MonetaryBox](), 0L) {
          case (txsForTx, oneTxs) =>
            val outputsAmount: Long = txsForTx._1.map(_.amount).sum
            if (outputsAmount > settings.transactions.requiredAmount + settings.transactions.feeAmount)
              (txsForTx._1, outputsAmount)
            else (oneTxs :: txsForTx._1, outputsAmount)
        }
        val resultsBoxes: List[MonetaryBox] = innerBoxes.diff(boxesForTx._1)
        logger.info(s"Current number of boxes: ${innerBoxes.size}. Boxes after diff: ${resultsBoxes.size}. " +
          s"Number of tx is: $number. Tx type is: $txsType")
        val useOutput: Seq[(MonetaryBox, None.type)] = boxesForTx._1.map(_ -> None)
        if (boxesForTx._2 > settings.transactions.requiredAmount + settings.transactions.feeAmount)
          generateAndSendTransaction(useOutput, txsType)
        resultsBoxes
      case _ =>
        logger.info(s"Not enough boxes for new tx.")
        List()
    }

  def generateAndSendTransaction(useOutput: Seq[(MonetaryBox, None.type)], txsType: String): Future[Unit] = Future {
    val transaction: EncryTransaction = txsType match {
      case settings.transactions.dataTx =>
        Transaction.dataTransactionScratch(
          privKey,
          settings.transactions.feeAmount,
          System.currentTimeMillis(),
          useOutput,
          PubKeyLockedContract(privKey.publicImage.pubKeyBytes).contract,
          settings.transactions.requiredAmount - settings.transactions.feeAmount,
          utils.Random.randomBytes(settings.transactions.dataTxSize),
          settings.transactions.numberOfCreatedDirectives
        )
      case settings.transactions.paymentTx =>
        Transaction.defaultPaymentTransaction(
          privKey,
          settings.transactions.feeAmount,
          System.currentTimeMillis(),
          useOutput,
          privKey.publicImage.address.address,
          settings.transactions.requiredAmount - settings.transactions.feeAmount,
          settings.transactions.numberOfCreatedDirectives
        )
    }
    settings.peers.foreach(NetworkService.commitTransaction(_, transaction))
    logger.info(s"Generated and sent new transaction with id: ${Algos.encode(transaction.id)}")
  }
}

object Generator {
  def props(settings: Settings, privKey: PrivateKey25519, walletStorageReader: WalletStorageReader): Props =
    Props(new Generator(settings, privKey, walletStorageReader))
}