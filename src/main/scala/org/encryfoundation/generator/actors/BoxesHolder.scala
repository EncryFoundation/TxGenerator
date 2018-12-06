package org.encryfoundation.generator.actors

import akka.actor.{Actor, Cancellable, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.BoxesHolder.{AskBoxesFromGenerator, BoxesAnswerToGenerator, BoxesRequestFromLocal}
import org.encryfoundation.generator.transaction.box.EncryBaseBox
import org.encryfoundation.generator.utils.Settings
import org.encryfoundation.generator.wallet.WalletStorage
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class BoxesHolder(settings: Settings, walletStorage: WalletStorage) extends Actor with StrictLogging {

  val defaultAskTime: FiniteDuration = settings.boxesHolderSettings.askBoxesFromLocalDBPeriod.seconds

  val takeBoxesSchedule: Cancellable = context.system.scheduler.schedule(
    5.seconds,
    settings.boxesHolderSettings.askBoxesFromLocalDBPeriod.seconds,
    self,
    BoxesRequestFromLocal
  )

  override def receive: Receive = boxesHolderBehavior()

  def boxesHolderBehavior(boxes: List[EncryBaseBox] = List()): Receive = {
    case BoxesRequestFromLocal =>
      val newBoxesFromDB: List[EncryBaseBox] = walletStorage.allBoxes.toList
      logger.info(s"Got new request for new boxes from DB from local. Qty of new boxes is: ${newBoxesFromDB.size}.")
      context.become(boxesHolderBehavior(newBoxesFromDB))
    case AskBoxesFromGenerator =>
      val boxesForRequest: List[EncryBaseBox] = boxes.take(settings.boxesHolderSettings.qtyOfAskedBoxes)
      sender() ! BoxesAnswerToGenerator(boxesForRequest)
      val resultBoxes: List[EncryBaseBox] = boxes.diff(boxesForRequest)
      logger.info(s"Got new request for new boxes from generator. Gave boxes: ${boxesForRequest.size}. " +
        s"New qty of boxes is: ${resultBoxes.size}.")
      context.become(boxesHolderBehavior(resultBoxes))
  }
}

object BoxesHolder {

  def props(settings: Settings, walletStorage: WalletStorage): Props = Props(new BoxesHolder(settings, walletStorage))

  case object BoxesRequestFromLocal
  case object AskBoxesFromGenerator
  case class BoxesAnswerToGenerator(boxes: List[EncryBaseBox])
}