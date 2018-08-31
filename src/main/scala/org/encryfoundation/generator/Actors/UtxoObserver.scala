package org.encryfoundation.generator.Actors

import java.net.InetSocketAddress
import akka.actor.{Actor, Cancellable}
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.Actors.Generator.Utxos
import org.encryfoundation.generator.Actors.UtxoObserver.RequestUtxos
import org.encryfoundation.generator.GeneratorApp.settings
import org.encryfoundation.generator.transaction.box.Box
import org.encryfoundation.generator.utils.NetworkService
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

class UtxoObserver(host: InetSocketAddress) extends Actor {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  var pool: Map[String, Box] = Map.empty

  val utxosRequest: Cancellable = context.system.scheduler
    .schedule(initialDelay = 5 seconds, interval = settings.nodePollingInterval seconds)(fetchUtxos())

  override def receive: Receive = {
    case RequestUtxos(qty) =>
      val takeQty: Int =
        if (qty < 0) pool.size
        else if (qty <= pool.size) qty
        else pool.size
      val outputs: Map[String, Box] = pool.take(takeQty)
      pool --= outputs.keys
      sender() ! Utxos(outputs.values.toSeq)
  }

  def fetchUtxos(): Unit = NetworkService.requestUtxos(host)
    .map { outputs => pool ++= Map(outputs.map(o => Algos.encode(o.id) -> o): _*) }
}

object UtxoObserver {

  case class RequestUtxos(qty: Int)

}
