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

  var pool: LocalPool                = LocalPool()
  var walletStorage: WalletStorage   = walletStorageReader.createWalletStorage
  val defaultAskTime: FiniteDuration = settings.boxesHolderSettings.askBoxesFromLocalDBPeriod.seconds
  context.system.scheduler.schedule(5.seconds, defaultAskTime, self, BoxesRequestFromLocal)

  override def receive: Receive = {
    case BoxesRequestFromLocal =>
      walletStorage = walletStorageReader.createWalletStorage
      pool = pool.updatePool(walletStorage.allBoxes.map(_.asInstanceOf[MonetaryBox]).toList)
      logger.info(s"New local pool after getting new outputs: $pool")
    case AskBoxesFromGenerator =>
      logger.info(s"Pool before sending outputs: $pool")
      val getOutputsResult: (LocalPool, List[MonetaryBox]) =
        pool.getOutputs(settings.transactions.numberOfRequestedBoxes)
      pool = getOutputsResult._1
      sender() ! BoxesAnswerToGenerator(getOutputsResult._2)
      logger.info(s"Pool after sending outputs: $pool")
    case ReturnedBoxes(returnedBoxes) =>
      pool = pool.addUnusedToPool(returnedBoxes)
      logger.info(s"Pool after backing unused: $pool")
  }
}

object BoxesHolder {

  def props(settings: Settings, walletStorageReader: WalletStorageReader): Props =
    Props(new BoxesHolder(settings, walletStorageReader))

  case object BoxesRequestFromLocal
  case object AskBoxesFromGenerator
  case class  BoxesAnswerToGenerator(boxes: List[MonetaryBox])
  case class  ReturnedBoxes(boxes: List[MonetaryBox])
}