package org.encryfoundation.generator.actors

import java.net.InetSocketAddress
import akka.actor.{Actor, Cancellable}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.actors.Generator.Utxos
import org.encryfoundation.generator.actors.UtxoObserver.{CleanUsedUtxos, FetchedUtxos, RequestUtxos}
import org.encryfoundation.generator.GeneratorApp.settings
import org.encryfoundation.generator.actors.InfluxActor.RequestedFromLocalOutputs
import org.encryfoundation.generator.transaction.box.Box
import org.encryfoundation.generator.utils.NetworkService
import scala.collection.immutable.TreeSet
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

class UtxoObserver(host: InetSocketAddress) extends Actor with StrictLogging {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  val utxosRequest: Cancellable = context.system.scheduler
    .schedule(initialDelay = 20 seconds, interval = settings.nodePollingInterval seconds)(fetchUtxos())

  context.system.scheduler.schedule(600 second, 600 second) {
    self ! CleanUsedUtxos
  }

  override def receive: Receive = changePool()

  def changePool(pool: Map[String, Box] = Map(), usedUtxos: TreeSet[String] = TreeSet()): Receive = {
    case RequestUtxos(qty) =>
      val takeQty: Int = if (qty <= pool.size) qty else pool.size
      val outputs: Map[String, Box] = pool.take(takeQty)
      sender() ! Utxos(outputs.values.toSeq)
      context.become(changePool(pool -- outputs.keys, usedUtxos ++ outputs.keySet))
      if (settings.influxDB.enable)
        context.system.actorSelection("user/influxDB") ! RequestedFromLocalOutputs(pool.size, outputs.size)
      logger.info("Generator asked observer for boxes")
    case FetchedUtxos(utxos) =>
      val boxes: Map[String, Box] = pool ++ Map(utxos.map(o => Algos.encode(o.id) -> o): _*)
      context.become(changePool(boxes.filterKeys(output => !usedUtxos.contains(output)), usedUtxos))
    case CleanUsedUtxos => context.become(changePool(pool, TreeSet()))
  }

  def fetchUtxos(): Unit = {
    NetworkService.requestUtxos(host).foreach(self ! FetchedUtxos(_))
    logger.info("Observer got outputs from remote nodes")
  }
}

object UtxoObserver {

  case class RequestUtxos(qty: Int)

  case class FetchedUtxos(utxos: Seq[Box])

  case class CleanUsedUtxos()

}