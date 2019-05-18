package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.actors.InfluxActor._
import org.encryfoundation.generator.modifiers.box.AssetBox
import org.encryfoundation.generator.utils.{NetworkService, Node, Settings}
import com.google.common.base.Charsets
import com.google.common.hash.{BloomFilter, Funnels}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BoxesHolder(settings: Settings,
                  influx: Option[ActorRef],
                  peer: Node) extends Actor with StrictLogging {

  context.system.scheduler.schedule(
    5.seconds, settings.boxesHolderSettings.askingAPIFrequency, self, RequestForNewBoxesFromApi
  )

  var bloomFilter: BloomFilter[String] = initBloomFilter

  context.system.scheduler.schedule(
    settings.boxesHolderSettings.bloomFilterCleanupInterval,
    settings.boxesHolderSettings.bloomFilterCleanupInterval) {
    bloomFilter = initBloomFilter
  }

  override def receive: Receive = boxesHolderBehavior()

  def boxesHolderBehavior(pool: List[Batch] = List()): Receive = {
    case BoxesFromApi(boxes) =>
      logger.info(s"BoxesHolder got message `BoxesFromApi`. Number of received boxes is: ${boxes.size}.")
      val batchesPool: List[Batch] = batchesForTransactions(boxes)
      val newBatches: List[Batch] = pool ++: batchesPool
      influx.foreach(_ ! PoolState(newBatches.size))
      logger.info(s"Number of batches is: ${newBatches.size}")
      context.become(boxesHolderBehavior(newBatches))

    case AskBoxesFromGenerator =>
      logger.info(s"BoxesHolder got message `AskBoxesFromGenerator`. Current pool is: ${pool.size}")
      val batchesAfterMT: List[Batch] =
        if (settings.transactions.numberOfMonetaryTxs > 0) {
          val batchesForTxs: List[Batch] = pool.take(settings.transactions.numberOfMonetaryTxs)
          batchesForTxs.foreach(batch => sender() ! BoxesForGenerator(batch.boxes, 2))
          pool.drop(settings.transactions.numberOfMonetaryTxs)
        } else pool
      influx.foreach(_ ! PoolState(batchesAfterMT.size))

      val batchesAfterDT: List[Batch] =
        if (settings.transactions.numberOfDataTxs > 0) {
          val batchesForTxs: List[Batch] = batchesAfterMT.take(settings.transactions.numberOfDataTxs)
          batchesForTxs.foreach(batch => sender() ! BoxesForGenerator(batch.boxes, 1))
          batchesAfterMT.drop(settings.transactions.numberOfDataTxs)
        } else batchesAfterMT
      influx.foreach(_ ! PoolState(batchesAfterDT.size))

      logger.info(s"Number of batches before diff: ${pool.size}.")
      logger.info(s"Number of batches after diff: ${batchesAfterDT.size}.")

      val batchesAfterMultisigTx: List[Batch] =
        if (settings.transactions.numberOfMultisigTxs > 0) {
          val batchesForTxs: List[Batch] = batchesAfterDT.take(settings.transactions.numberOfMultisigTxs)
          batchesForTxs.foreach(batch => sender() ! BoxesForGenerator(batch.boxes, 3))
          batchesAfterDT.drop(settings.transactions.numberOfMultisigTxs)
        } else batchesAfterDT
      influx.foreach(_ ! PoolState(batchesAfterMultisigTx.size))

      logger.info(s"Number of batches before diff: ${pool.size}.")
      logger.info(s"Number of batches after diff: ${batchesAfterMultisigTx.size}.")

      influx.foreach(_ ! SentBatches(batchesAfterMultisigTx.size))
      context.become(boxesHolderBehavior(batchesAfterMultisigTx))

    case AskBoxesForMultisigSigning(txs) =>
      logger.info(s"BoxesHolder got message `AskBoxesForMultisigSigning`. Current pool is: ${pool.size}, and number of txs is ${txs.size}")
      txs.foreach(tx => sender() ! BoxesForGenerator(List.empty, 4, Some(tx)))
      logger.info(s"Number of batches after diff: ${pool.size}.")

    case RequestForNewBoxesFromApi =>
      if (pool.size < settings.boxesHolderSettings.poolSize) {
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
      if (request.nonEmpty && to < settings.boxesHolderSettings.maxPoolSize) {
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
    Funnels.stringFunnel(Charsets.UTF_8),
    settings.boxesHolderSettings.bloomFilterCapacity,
    settings.boxesHolderSettings.bloomFilterFailureProbability
  )
}

object BoxesHolder {
  def props(settings: Settings, influx: Option[ActorRef], peer: Node): Props =
    Props(new BoxesHolder(settings, influx, peer))

  case object RequestForNewBoxesFromApi

  case object AskBoxesFromGenerator

  case class BoxesFromApi(list: List[AssetBox])

  case class BoxesForGenerator(list: List[AssetBox], txType: Int, forTx: Option[String] = None)

  case class Batch(boxes: List[AssetBox])

  case class  AskBoxesForMultisigSigning(txs: Set[String])
}