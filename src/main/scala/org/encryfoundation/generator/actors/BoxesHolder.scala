package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
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
      logger.info(s"BoxesHolder got message `BoxesFromApi`. Size of boxes list is: ${boxes.size}. " +
        s"Current usedBoxes is: ${usedBoxes.size}")
      val cleanedBoxes: (List[AssetBox], TreeSet[String]) = cleanNewBoxesFromUsed(usedBoxes, boxes)
      influx.foreach(_ ! NewAndUsedOutputsInGeneratorMempool(cleanedBoxes._1.size, cleanedBoxes._2.size))
      val batchesPool: List[Batch] = batchesForTransactions(cleanedBoxes._1)
      logger.info(s"Number of batches is: ${batchesPool.size}")
      context.become(boxesHolderBehavior(batchesPool, cleanedBoxes._2))

    case AskBoxesFromGenerator =>
      logger.info(s"BoxesHolder got message `AskBoxesFromGenerator`. Current pool is: ${pool.size}")
      val batchesForTxs: List[Batch] = pool.takeRight(settings.transactions.numberOfMonetaryTxs)
      val newUsed: TreeSet[String] = batchesForTxs.flatMap(_.boxes.map(box => Algos.encode(box.id))).to[TreeSet]
      if (settings.transactions.numberOfMonetaryTxs > 0)
        batchesForTxs.foreach(txs => sender() ! BoxesForGenerator(txs.boxes, 2))
      logger.info(s"Number of batches before diff: ${pool.size}.")
      val resultedBatches: List[Batch] = pool.diff(batchesForTxs)
      logger.info(s"Number of batches after diff: ${resultedBatches.size}.")
      influx.foreach(_ ! SentBatches(pool.size - resultedBatches.size))
      context.become(boxesHolderBehavior(resultedBatches, usedBoxes ++ newUsed))

    case TimeToClean =>
      logger.info(s"Used boxes has been cleaned.")
      context.become(boxesHolderBehavior(pool, TreeSet()))
  }

  def batchesForTransactions(list: List[AssetBox]): List[Batch] = {
    val batchesList: (List[Batch], Batch, Long) = list.foldLeft(List[Batch](), Batch(List()), 0L) {
      case ((listBatches, batch, amount), box) =>
        val newBatch: List[AssetBox] = box :: batch.boxes
        val newAmount: Long = amount + box.amount
        if (newAmount > settings.transactions.feeAmount) (Batch(newBatch) :: listBatches, Batch(List()), 0)
        else (listBatches, Batch(newBatch), newAmount)
    }
    batchesList._1
  }

  def cleanNewBoxesFromUsed(usedB: TreeSet[String], newB: List[AssetBox]): (List[AssetBox], TreeSet[String]) = {
    val newBMap: Map[String, AssetBox] = Map(newB.map(k => Algos.encode(k.id) -> k): _*)
    logger.info(s"CleanNewBoxesFromUsed: New boxes map size is: ${newBMap.size}")
    val usedAndNewBoxes: (TreeSet[String], Map[String, AssetBox]) =
      usedB.foldLeft(TreeSet[String](), newBMap) { case ((newUsed, newBoxes), used) =>
        newBoxes.get(used) match {
          case Some(_) => (newUsed + used, newBoxes - used)
          case None => (newUsed, newBoxes)
        }
      }
    logger.info(s"CleanNewBoxesFromUsed: Used - ${usedAndNewBoxes._1.size}. New - ${usedAndNewBoxes._2.size}")
    (usedAndNewBoxes._2.values.toList, usedAndNewBoxes._1)
  }

  def getBoxes: Future[Unit] = NetworkService.requestUtxos(peer)
    .map(_.collect { case mb: AssetBox if mb.tokenIdOpt.isEmpty => mb })
    .map(boxes => self ! BoxesFromApi(boxes))
}

object BoxesHolder {
  def props(settings: Settings, influx: Option[ActorRef], peer: Node): Props =
    Props(new BoxesHolder(settings, influx, peer))

  case object AskBoxesFromGenerator

  case object TimeToClean

  case class BoxesFromApi(list: List[AssetBox])

  case class BoxesForGenerator(list: List[AssetBox], txType: Int)

  case class Batch(boxes: List[AssetBox])

}