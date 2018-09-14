package org.encryfoundation.generator.actors

import java.net.InetSocketAddress
import akka.actor.{Actor, Cancellable}
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.actors.Generator.Utxos
import org.encryfoundation.generator.actors.UtxoObserver.RequestUtxos
import org.encryfoundation.generator.GeneratorApp.settings
import org.encryfoundation.generator.actors.InfluxActor.{IncomeOutputsMessage, RequestUtxoMessage}
import org.encryfoundation.generator.transaction.box.Box
import org.encryfoundation.generator.utils.NetworkService
import scala.collection.immutable.TreeSet
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

class UtxoObserver(host: InetSocketAddress) extends Actor {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  var pool: Map[String, Box] = Map.empty
  var usedUtxsos: TreeSet[String] = TreeSet.empty

  val utxosRequest: Cancellable = context.system.scheduler
    .schedule(initialDelay = 5 seconds, interval = settings.nodePollingInterval seconds)(fetchUtxos())

  context.system.scheduler.schedule(360 second, 360 second) {
    usedUtxsos = TreeSet.empty
  }

  override def receive: Receive = {
    case RequestUtxos(qty) =>
      val takeQty: Int =
        if (qty < 0) pool.size
        else if (qty <= pool.size) qty
        else pool.size
      //        qty match {
      //        case num: Int if num <= pool.size && num >= 0 => num
      //        case _ => pool.size
      //      }
      val outputs: Map[String, Box] = pool.take(takeQty)
      pool --= outputs.keys
      usedUtxsos ++= outputs.keySet
      context.system.actorSelection("user/influxDB") ! RequestUtxoMessage(pool.size, outputs.size)
      sender() ! Utxos(outputs.values.toSeq)
  }

  def fetchUtxos(): Unit = {
    NetworkService.requestUtxos(host).map { outputs =>
      pool ++= Map(outputs.map(o => Algos.encode(o.id) -> o): _*)
      println(pool.size)
      pool = pool.filterKeys(output => !usedUtxsos.contains(output))
      println(pool.size)
      context.system.actorSelection("user/influxDB") ! IncomeOutputsMessage(outputs.size, pool.size)
    }
  }
}

object UtxoObserver {

  case class RequestUtxos(qty: Int)

}
