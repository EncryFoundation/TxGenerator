package org.encryfoundation.generator.actors

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.transaction.box.{AssetBox, EncryBaseBox}
import org.encryfoundation.generator.utils.Settings
import org.encryfoundation.generator.wallet.{WalletStorage, WalletStorageReader}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class BoxesHolder(settings: Settings, walletStorageReader: WalletStorageReader) extends Actor with StrictLogging {

  context.system.scheduler
    .schedule(5.seconds, settings.boxesHolderSettings.getBoxesFromIODbPeriod.seconds, self, RequestBoxesFromIODb)

  context.system.scheduler
    .schedule(
      settings.boxesHolderSettings.periodOfCleaningPool.seconds,
      settings.boxesHolderSettings.periodOfCleaningPool.seconds,
      self,
      TimeToClean
    )

  override def receive: Receive = boxesHolderBehavior()

  def boxesHolderBehavior(pool: LocalPool = LocalPool(),
                          walletStorage: WalletStorage = walletStorageReader.createWalletStorage): Receive = {
    case RequestBoxesFromIODb =>
      logger.info(s"BoxesHolder got message `RequestBoxesFromIODb`. Current pool is: $pool")
      val updatedWalletStorage: WalletStorage = walletStorageReader.createWalletStorage
      val getAllBoxes: List[EncryBaseBox] = walletStorage.allBoxes.toList
      logger.info(s"Number of transactions received is: ${getAllBoxes.size}.")
      val castedToAssetBoxes: List[AssetBox] = getAllBoxes.collect {
        case mb: AssetBox if mb.tokenIdOpt.isEmpty => mb
      }
      logger.info(s"Number of collected transactions is: ${castedToAssetBoxes.size}.")
      val updatedPool: LocalPool = pool.updatePool(castedToAssetBoxes)
      logger.info(s"New local pool after getting new outputs: $updatedPool.")
      context.become(boxesHolderBehavior(updatedPool, updatedWalletStorage))

    case AskBoxesFromGenerator =>
      logger.info(s"BoxesHolder got message `AskBoxesFromGenerator`. Current pool is: $pool")
      val updatedPool: (LocalPool, List[AssetBox])    = pool.getECOutputs
      logger.info(s"Number of taken boxes: ${updatedPool._2.size}. Pool after taken is: ${updatedPool._1}")
      val lastBoxesDataTxs: List[AssetBox]            = findBoxesForNTransactions(
        settings.transactions.numberOfDataTxs, updatedPool._2, 1
      )
      logger.info(s"Last boxes after generation dataTxs: ${lastBoxesDataTxs.size}")
      val lastBoxesMonetaryTxs: List[AssetBox]        = findBoxesForNTransactions(
        settings.transactions.numberOfMonetaryTxs, lastBoxesDataTxs, 2
      )
      logger.info(s"Last boxes after generation monetaryTxs: ${lastBoxesMonetaryTxs.size}")
      val lastPool: LocalPool                         = updatedPool._1.addUnusedECToPool(lastBoxesMonetaryTxs)
      logger.info(s"Last pool after generation txs: $lastPool")
      context.become(boxesHolderBehavior(lastPool))

    case TimeToClean => context.become(boxesHolderBehavior(LocalPool(pool.encryCoinBoxes)))
  }

  def findBoxesForNTransactions(numberOfTxs: Int, encryCoinBoxes: List[AssetBox], txType: Int): List[AssetBox] =
    if (numberOfTxs > 0 && encryCoinBoxes.nonEmpty) {
      (0 until numberOfTxs).foldLeft(encryCoinBoxes) {
        case (ecBox, numOfTx) if ecBox.nonEmpty =>
          val neededAmount: Long = settings.transactions.feeAmount + settings.transactions.requiredAmount
          logger.info(s"EncryCoinBoxes is nonEmpty. Needed amount is: $neededAmount. Current number of tx is: $numOfTx.")
          val ecBoxes: (List[AssetBox], Long) = findBoxesForNeededAmount(ecBox, neededAmount)
          if (ecBoxes._2 > neededAmount) {
            logger.info(s"Enough amount for tx. Number of boxes is:  ${ecBoxes._1.size}.")
            sender() ! BoxesForGenerator(ecBoxes._1, txType)
          } else logger.info(s"Not enough amount for tx. Number of boxes is:  ${ecBoxes._1.size}.")
          val ecBoxesMap: Map[String, AssetBox] = Map(ecBoxes._1.map(k => Algos.encode(k.id) -> k): _*)
          val encryCoinBoxesMap: Map[String, AssetBox] = Map(ecBox.map(k => Algos.encode(k.id) -> k): _*)
          (encryCoinBoxesMap -- ecBoxesMap.keys).values.toList
        case _ =>
          logger.info(s"Not enough boxes for new tx.")
          List()
      }
    } else {
      logger.info(s"Number of txs is: $numberOfTxs. Number of boxes is: ${encryCoinBoxes.size}")
      encryCoinBoxes
    }

  def findBoxesForNeededAmount(boxes: List[AssetBox], amount: Long): (List[AssetBox], Long) =
    boxes.foldLeft(List[AssetBox](), 0L) { case ((collection, _), box) =>
      val currentAmount: Long = collection.map(_.amount).sum
      if (currentAmount > amount) (collection, currentAmount)
      else (box :: collection, 0)
    }
}

object BoxesHolder {

  def props(settings: Settings, walletStorageReader: WalletStorageReader): Props =
    Props(new BoxesHolder(settings, walletStorageReader))

  case object RequestBoxesFromIODb
  case object AskBoxesFromGenerator
  case object TimeToClean
  case class  BoxesForGenerator(list: List[AssetBox], txType: Int)
}