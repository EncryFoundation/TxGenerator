package org.encryfoundation.generator.actors

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.transaction.box.MonetaryBox
import org.encryfoundation.generator.utils.Settings
import org.encryfoundation.generator.wallet.{WalletStorage, WalletStorageReader}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class BoxesHolder(settings: Settings, walletStorageReader: WalletStorageReader) extends Actor with StrictLogging {

  val defaultAskTime: FiniteDuration = settings.boxesHolderSettings.askBoxesFromLocalDBPeriod.seconds
  context.system.scheduler.schedule(5.seconds, defaultAskTime, self, BoxesRequestFromLocal)

  override def receive: Receive = boxesHolderBehavior()

  def boxesHolderBehavior(boxes: List[MonetaryBox] = List(),
                          walletStorage: WalletStorage = walletStorageReader.createWalletStorage,
                          localPoolOfUsedBoxes: List[MonetaryBox] = List()): Receive = {
    case BoxesRequestFromLocal =>
      val newWalletStorage: WalletStorage = walletStorageReader.createWalletStorage
      val newBoxesFromDB: List[MonetaryBox] = walletStorage.allBoxes.map(_.asInstanceOf[MonetaryBox]).toList
      logger.info(s"Got new request for new boxes from DB from local. Qty of new boxes is: ${newBoxesFromDB.size}.")
      context.become(boxesHolderBehavior(newBoxesFromDB, newWalletStorage))
    case AskBoxesFromGenerator =>
      val boxesForRequest: List[MonetaryBox] = boxes.take(settings.transactions.numberOfRequestedBoxes)
      sender() ! BoxesAnswerToGenerator(boxesForRequest)
      val resultBoxes: List[MonetaryBox] = boxes.drop(settings.transactions.numberOfRequestedBoxes)
      logger.info(s"Got new request for new boxes from generator. Gave boxes: ${boxesForRequest.size}. " +
        s"New qty of boxes is: ${resultBoxes.size}.")
      context.become(boxesHolderBehavior(resultBoxes))
  }
}

object BoxesHolder {

  def props(settings: Settings, walletStorageReader: WalletStorageReader): Props =
    Props(new BoxesHolder(settings, walletStorageReader))

  case object BoxesRequestFromLocal
  case object AskBoxesFromGenerator
  case class  BoxesAnswerToGenerator(boxes: List[MonetaryBox])
}