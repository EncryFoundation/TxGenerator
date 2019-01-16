package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.GeneratorApp.system
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.actors.InfluxActor._
import org.encryfoundation.generator.transaction.box.AssetBox
import org.encryfoundation.generator.utils.{NetworkService, Node, Settings}
import scala.collection.immutable.TreeSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BoxesHolder(settings: Settings,
                  influx: Option[ActorRef],
                  peer: Node) extends Actor with StrictLogging {

  val cleanPeriod: FiniteDuration = settings.boxesHolderSettings.periodOfCleaningPool.seconds
  context.system.scheduler.schedule(cleanPeriod, cleanPeriod, self, TimeToClean)
  context.system.scheduler.schedule(5.seconds, settings.boxesHolderSettings.getBoxesFromApi.seconds)(getBoxes)

  override def receive: Receive = boxesHolderBehavior()

  def boxesHolderBehavior(pool: List[Batch] = List(), usedBoxes: TreeSet[String] = TreeSet()): Receive = {
    case BoxesFromApi(boxes) =>
      logger.info(s"BoxesHolder got message `BoxesFromApi`. Current pool is: ${pool.size}. " +
        s"Current usedBoxes is: ${usedBoxes.size}")
      val foundResult: (List[AssetBox], TreeSet[String]) =
        findDifferenceBetweenUsedAndNewBoxes(usedBoxes, boxes)
      influx.foreach(_ ! NewAndUsedOutputsInGeneratorMempool(foundResult._1.size, foundResult._2.size))
      logger.info(s"Number of foundResult is: ${foundResult._1.size},  ${foundResult._2.size}.")
      val batchesPool: List[Batch] = batchesForTransactions(foundResult._1)
      logger.info(s"Number of batches is: ${batchesPool.size}")
      context.become(boxesHolderBehavior(batchesPool ::: pool, foundResult._2))

    case AskBoxesFromGenerator =>
      logger.info(s"BoxesHolder got message `AskBoxesFromGenerator`. Current pool is: ${pool.size}")
      val batchesForDataTxs: List[Batch] = pool.takeRight(settings.transactions.numberOfDataTxs)
      if (settings.transactions.numberOfDataTxs > 0)
        batchesForDataTxs.foreach(txs => sender() ! BoxesForGenerator(txs.boxes, 1))
      logger.info(s"Number of batches before diff: ${pool.size}.")
      val lastBatches: List[Batch] = pool.diff(batchesForDataTxs)
      logger.info(s"Number of batches after diff: ${lastBatches.size}.")
      val batchesForMonetaryTxs: List[Batch] = lastBatches.takeRight(settings.transactions.numberOfMonetaryTxs)
      if (settings.transactions.numberOfMonetaryTxs > 0)
        batchesForMonetaryTxs.foreach(txs => sender() ! BoxesForGenerator(txs.boxes, 2))
      logger.info(s"Number of batches before diff: ${lastBatches.size}.")
      val resultedBatches: List[Batch] = lastBatches.diff(batchesForMonetaryTxs)
      logger.info(s"Number of batches after diff: ${resultedBatches.size}.")
      influx.foreach(_ ! SentBatches(pool.size - resultedBatches.size))
      context.become(boxesHolderBehavior(resultedBatches, usedBoxes))

    case TimeToClean =>
      logger.info(s"Mempool has been cleaned.")
      context.become(boxesHolderBehavior(pool))
  }

  def batchesForTransactions(list: List[AssetBox]): List[Batch] = {
    val startTime: Long = System.currentTimeMillis()
    val batchesList = list.foldLeft(List[Batch](), Batch(List()), 0L) { case ((listBatches, batch, amount), box) =>
      val newBatch: List[AssetBox] = box :: batch.boxes
      val newAmount: Long = amount + box.amount
      if (newAmount > settings.transactions.feeAmount + settings.transactions.requiredAmount)
        (Batch(newBatch) :: listBatches, Batch(List()), 0)
      else (listBatches, Batch(newBatch), newAmount)
    }
    influx.foreach(infl => infl ! FindBatchesTimeSeconds((System.currentTimeMillis() - startTime) / 1000))
    batchesList._1
  }

  def findDifferenceBetweenUsedAndNewBoxes(usedB: TreeSet[String],
                                           newB: List[AssetBox]): (List[AssetBox], TreeSet[String]) = {
    val newBMap: Map[String, AssetBox] = Map(newB.map(k => Algos.encode(k.id) -> k): _*)
    logger.info(s"newBMap: ${newBMap.size}")
    val filteredNewB: Map[String, AssetBox] = newBMap.filterKeys(key => !usedB.contains(key))
    logger.info(s"filteredNewB: ${filteredNewB.size}")
    logger.info(s"newBMap.keys.to[TreeSet].size ${newBMap.keys.to[TreeSet].size}")
    (filteredNewB.values.toList, newBMap.keys.to[TreeSet])
  }

  def getBoxes: Future[Unit] = {
    NetworkService.requestUtxos(peer)
      .map(_.collect { case mb: AssetBox if mb.tokenIdOpt.isEmpty => mb })
      .map(boxes => self ! BoxesFromApi(boxes))
  }
}

object BoxesHolder {
  def props(settings: Settings, influx: Option[ActorRef], peer: Node): Props =
    Props(new BoxesHolder(settings, influx, peer))

  case object AskBoxesFromGenerator
  case object TimeToClean
  case class  BoxesFromApi(list: List[AssetBox])
  case class  BoxesForGenerator(list: List[AssetBox], txType: Int)
  case class  Batch(boxes: List[AssetBox])
}