package org.encryfoundation.generator.actors

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.transaction.box.EncryBaseBox
import org.encryfoundation.generator.utils.Settings
import org.encryfoundation.generator.wallet.{WalletStorage, WalletStorageReader}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class BoxesHolder(settings: Settings, walletStorageReader: WalletStorageReader) extends Actor with StrictLogging {

  var walletStorage: WalletStorage   = walletStorageReader.createWalletStorage
  val defaultAskTime: FiniteDuration = settings.boxesHolderSettings.askBoxesFromLocalDBPeriod.seconds

  context.system.scheduler.schedule(5.seconds, defaultAskTime, self, BoxesRequestFromLocal)

  override def receive: Receive = boxesHolderBehavior()

  def boxesHolderBehavior(boxes: List[EncryBaseBox] = List()): Receive = {
    case BoxesRequestFromLocal =>
      walletStorage = walletStorageReader.createWalletStorage
      val newBoxesFromDB: List[EncryBaseBox] = walletStorage.allBoxes.toList
      logger.info(s"Got new request for new boxes from DB from local. Qty of new boxes is: ${newBoxesFromDB.size}.")
      context.become(boxesHolderBehavior(newBoxesFromDB))
    case AskBoxesFromGenerator =>
      val boxesForRequest: List[EncryBaseBox] = boxes.take(settings.transactions.totalNumberOfTxs)
      sender() ! BoxesAnswerToGenerator(boxesForRequest)
      val resultBoxes: List[EncryBaseBox] = boxes.drop(settings.transactions.totalNumberOfTxs)
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
  case class  BoxesAnswerToGenerator(boxes: List[EncryBaseBox])
}