package org.encryfoundation.generator.actors

import java.net.InetSocketAddress
import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.actors.Generator.Utxos
import org.encryfoundation.generator.actors.UtxoObserver.{CleanUsedUtxos, FetchedUtxos, RequestUtxos}
import org.encryfoundation.generator.GeneratorApp.settings
import org.encryfoundation.generator.actors.InfluxActor.{RequestedFromLocalOutputs, RequestedFromRemoteOutputs}
import org.encryfoundation.generator.transaction.box.Box
import org.encryfoundation.generator.utils.NetworkService
import scala.collection.immutable.TreeSet
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

class UtxoObserver(host: InetSocketAddress) extends Actor with StrictLogging {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  context.system.scheduler.schedule(initialDelay = 20 seconds, interval =
    settings.nodePollingInterval seconds)(fetchUtxos())

  context.system.scheduler.schedule(600 second, 600 second) {
    self ! CleanUsedUtxos
  }

  override def receive: Receive = changePool()

  def changePool(pool: Map[String, Box] = Map(), usedUtxos: TreeSet[String] = TreeSet()): Receive = {
    case RequestUtxos(qty) =>
      val takeQty: Int = if (qty <= pool.size) qty else pool.size
      val outputs: Map[String, Box] = pool.take(takeQty)
      sender() ! Utxos(outputs.values.toSeq)
      val currentPool: Map[String, Box] = pool -- outputs.keys
      val currentUsedUtxos: TreeSet[String] = usedUtxos ++ outputs.keySet
      context.become(changePool(currentPool, currentUsedUtxos))
      if (settings.influxDB.enable)
        context.system.actorSelection("user/influxDB") ! RequestedFromLocalOutputs(pool.size, outputs.size)
      logger.info(s"Generator asked observer for boxes and get ${outputs.size}. Current pool is: ${currentPool.size}")
    case FetchedUtxos(utxos) =>
      val boxes: Map[String, Box] = pool ++ Map(utxos.map(o => Algos.encode(o.id) -> o): _*)
      context.become(changePool(boxes.filterKeys(output => !usedUtxos.contains(output)), usedUtxos))
      if (settings.influxDB.enable)
        context.system.actorSelection("user/influxDB") ! RequestedFromRemoteOutputs(pool.size)
    case CleanUsedUtxos =>
      logger.info(s"Clean UsedUtxos collection in UtxosObserver")
      context.become(changePool(pool, TreeSet()))
  }

  def fetchUtxos(): Unit = {
    NetworkService.requestUtxos(host).foreach { boxes =>
      logger.info(s"Observer got ${boxes.size} outputs from remote nodes")
      self ! FetchedUtxos(boxes)
    }
  }
}

object UtxoObserver {

  case class RequestUtxos(qty: Int)

  case class FetchedUtxos(utxos: Seq[Box])

  case class CleanUsedUtxos()

}