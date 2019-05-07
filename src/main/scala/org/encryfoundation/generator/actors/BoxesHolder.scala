package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.actors.InfluxActor._
import org.encryfoundation.generator.transaction.box.AssetBox
import org.encryfoundation.generator.utils.{NetworkService, Node, Settings}
import cats.instances.all._
import cats.kernel.Semigroup
import cats.syntax.semigroup._
import com.google.common.base.Charsets
import com.google.common.hash.{BloomFilter, Funnels}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BoxesHolder(settings: Settings,
                  influx: Option[ActorRef],
                  peer: Node) extends Actor with StrictLogging {

  val cleanPeriod: FiniteDuration = settings.boxesHolderSettings.periodOfCleaningPool.seconds

  context.system.scheduler.schedule(
    5.seconds, settings.boxesHolderSettings.getBoxesFromApi.seconds, self, RequestForNewBoxesFromApi
  )

  var bloomFilter: BloomFilter[String] = initBloomFilter

  context.system.scheduler.schedule(1800.seconds, 1800.seconds) {
    bloomFilter = initBloomFilter
  }

  /**
    * Semigroup class for Cancellable. In this case while adding two instance we need to choose left one.
    */
  implicit val cancellableSemigroup: Semigroup[Cancellable] = new Semigroup[Cancellable] {
    override def combine(x: Cancellable, y: Cancellable): Cancellable = x
  }

  override def receive: Receive = boxesHolderBehavior()

  def boxesHolderBehavior(pool: List[Batch] = List(), boxesForRemove: Map[String, Cancellable] = Map()): Receive = {
    case BoxesFromApi(boxes) =>
      logger.info(s"BoxesHolder got message `BoxesFromApi`. Number of received boxes is: ${boxes.size}.")
      //s"Current used boxes number is: ${boxesForRemove.size}.")
      //val (boxesForUse: List[AssetBox], usedBoxes: Map[String, Cancellable]) = cleanReceivedBoxesFromUsed(boxesForRemove, boxes)
      //logger.info(s"BoxesForUse number after comparison is: ${boxesForUse.size}. usedBoxes number is: ${usedBoxes.size}.")
      val batchesPool: List[Batch] = batchesForTransactions(boxes)
      val newBatches: List[Batch] = pool ++: batchesPool
      influx.foreach(_ ! NewAndUsedOutputsInGeneratorMempool(newBatches.size, boxesForRemove.size))
      logger.info(s"Number of batches is: ${newBatches.size}")
      context.become(boxesHolderBehavior(newBatches, Map.empty))

    case AskBoxesFromGenerator =>
      logger.info(s"BoxesHolder got message `AskBoxesFromGenerator`. Current pool is: ${pool.size}")
      val batchesForTxs: List[Batch] = pool.take(settings.transactions.numberOfMonetaryTxs)
      //      val usedBoxed: Map[String, Cancellable] = batchesForTxs.flatMap(_.boxes.map { box =>
      //        val boxId: String = Algos.encode(box.id)
      //        (boxId, context.system.scheduler.scheduleOnce(cleanPeriod, self, BoxForRemovingFromPool(boxId)))
      //      }).toMap
      //      logger.info(s"Updated number of used boxes: ${usedBoxed.size}")
      //      val totalNumberOfUsedBoxes: Map[String, Cancellable] = usedBoxed |+| boxesForRemove
      //      logger.info(s"Total number of used boxes: ${totalNumberOfUsedBoxes.size}")
      if (settings.transactions.numberOfMonetaryTxs > 0)
        batchesForTxs.foreach(batch => sender() ! BoxesForGenerator(batch.boxes, 2))
      logger.info(s"Number of batches before diff: ${pool.size}.")
      val resultedBatches: List[Batch] = pool.drop(settings.transactions.numberOfMonetaryTxs)
      logger.info(s"Number of batches after diff: ${resultedBatches.size}.")
      //      influx.foreach(_ ! NewAndUsedOutputsInGeneratorMempool(resultedBatches.map(_.boxes.size).sum, totalNumberOfUsedBoxes.size))
      influx.foreach(_ ! SentBatches(resultedBatches.size))
      context.become(boxesHolderBehavior(resultedBatches, Map.empty))

    case BoxForRemovingFromPool(id) =>
      logger.info(s"Received request for removing box with id: $id. Current number of boxes for remove is: ${boxesForRemove.size}.")
      logger.info(s"${boxesForRemove.get(id).isDefined}")
      val updatedUsedBoxesForRemove: Map[String, Cancellable] = boxesForRemove - id
      logger.info(s"Number of boxes for remove after deleting element is: ${updatedUsedBoxesForRemove.size}.")
      influx.foreach(_ ! NewAndUsedOutputsInGeneratorMempool(pool.map(_.boxes.size).sum, updatedUsedBoxesForRemove.size))
      context.become(boxesHolderBehavior(pool, updatedUsedBoxesForRemove))

    case RequestForNewBoxesFromApi =>
      if (pool.size < 1000) {
        logger.info(s"Current pool size is: ${pool.size}. Asking new boxes from api!")
        getBoxes(0, settings.boxesHolderSettings.rangeForAskingBoxes)
      }
      else logger.info(s"Current pool is: ${pool.size}. We won't ask new boxes from api!")
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

  def cleanReceivedBoxesFromUsed(usedB: Map[String, Cancellable],
                                 newB: List[AssetBox]): (List[AssetBox], Map[String, Cancellable]) = {
    val newBMap: Map[String, AssetBox] = Map(newB.map(k => Algos.encode(k.id) -> k): _*)
    logger.info(s"cleanReceivedBoxesFromUsed: New boxes map size is: ${newBMap.size}")
    val (usedBoxes: Map[String, Cancellable], newBoxes: Map[String, AssetBox]) =
      usedB.foldLeft(Map[String, Cancellable](), newBMap) {
        case ((newUsedCollection, newBoxesCollection), (id, timer)) => newBoxesCollection.get(id) match {
          case Some(_) => (newUsedCollection.updated(id, timer), newBoxesCollection - id)
          case None =>
            timer.cancel()
            (newUsedCollection, newBoxesCollection)
        }
      }
    logger.info(s"CleanNewBoxesFromUsed: Used - ${usedBoxes.size}. New - ${newBoxes.size}")
    (newBoxes.values.toList, usedBoxes)
  }

  def getBoxes(from: Int, to: Int): Future[Unit] =
    NetworkService.requestUtxos(peer, from, to).map { request =>
      logger.info(s"Boxes from API: ${request.size}")
      if (request.nonEmpty) {
        val newFrom: Int = from + settings.boxesHolderSettings.rangeForAskingBoxes
        val newTo: Int = to + settings.boxesHolderSettings.rangeForAskingBoxes
        getBoxes(newFrom, newTo)
        logger.info(s"Asking new boxes in range: $newFrom -> $newTo.")
      }
      request.collect { case mb: AssetBox if mb.tokenIdOpt.isEmpty && !bloomFilter.mightContain(Algos.encode(mb.id)) =>
        bloomFilter.put(Algos.encode(mb.id))
        mb
      }
    }.map(boxes => self ! BoxesFromApi(boxes))

  def initBloomFilter: BloomFilter[String] = BloomFilter.create(
    Funnels.stringFunnel(Charsets.UTF_8), 300000.toLong, 0.01
  )
}

object BoxesHolder {
  def props(settings: Settings, influx: Option[ActorRef], peer: Node): Props =
    Props(new BoxesHolder(settings, influx, peer))

  case object RequestForNewBoxesFromApi

  case object AskBoxesFromGenerator

  case class BoxesFromApi(list: List[AssetBox])

  case class BoxesForGenerator(list: List[AssetBox], txType: Int)

  case class Batch(boxes: List[AssetBox])

  case class BoxForRemovingFromPool(boxId: String)

}