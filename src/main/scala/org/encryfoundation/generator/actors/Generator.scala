package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.transaction.PubKeyLockedContract
import org.encryfoundation.generator.actors.BoxesHolder.{AskBoxesFromGenerator, BoxesAnswerToGenerator}
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
      val monetaryBoxes: List[MonetaryBox] = boxes.map(_.asInstanceOf[MonetaryBox])
      val lastBoxesAfterGeneratingDataTxs: List[MonetaryBox] = generateNTransactions(
        settings.transactions.numberOfDataTxs,
        monetaryBoxes,
        settings.transactions.dataTx
      )
      generateNTransactions(
        settings.transactions.totalNumberOfTxs - settings.transactions.numberOfDataTxs,
        lastBoxesAfterGeneratingDataTxs,
        settings.transactions.paymentTx
      )
  }

  def generateNTransactions(numberOfTxs: Int, monetaryBoxes: List[MonetaryBox], txsType: String): List[MonetaryBox] =
    (0 until numberOfTxs).foldLeft(monetaryBoxes) { case (innerBoxes, number) =>
      val boxesForTx: List[MonetaryBox] = innerBoxes.foldLeft(List[MonetaryBox]()) {
        case (txsForTx, oneTxs) =>
          val outputsAmount: Long = txsForTx.map(_.amount).sum
          if (outputsAmount > settings.transactions.requiredAmount + settings.transactions.feeAmount) {
            logger.info(s"Required amount is: ${settings.transactions.requiredAmount + settings.transactions.feeAmount}." +
              s" Boxes amount is: $outputsAmount")
            txsForTx
          }
          else oneTxs :: txsForTx
      }
      val resultsBoxes: List[MonetaryBox] = innerBoxes.diff(boxesForTx)
      logger.info(s"Current number of boxes: ${innerBoxes.size}. Boxes after diff: ${resultsBoxes.size}. " +
        s"Number of tx is: $number. Tx type is: $txsType")
      val useOutput: Seq[(MonetaryBox, None.type)] = boxesForTx.map(_ -> None)
      generateAndSendTransaction(useOutput, txsType)
      resultsBoxes
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