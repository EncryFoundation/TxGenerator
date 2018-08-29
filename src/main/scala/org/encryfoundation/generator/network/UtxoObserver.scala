package org.encryfoundation.generator.network

import java.net.InetSocketAddress
import akka.actor.{Actor, Cancellable}
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.Generator.Utxos
import org.encryfoundation.generator.network.UtxoObserver.RequestUtxos
import org.encryfoundation.generator.settings.NetworkSettings
import org.encryfoundation.generator.transaction.box.Box
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

case class UtxoObserver(host: InetSocketAddress,
                        network: NetworkService,
                        settings: NetworkSettings) extends Actor {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  var pool: Map[String, Box] = Map.empty

  val utxosRequest: Cancellable = context.system.scheduler
    .schedule(5.seconds, settings.nodePollingInterval)(fetchUtxos())

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

  def fetchUtxos(): Unit = network
    .requestUtxos(host)
    .map { outputs => pool ++= Map(outputs.map(o => Algos.encode(o.id) -> o):_*) }
}

object UtxoObserver {

  case class RequestUtxos(qty: Int)
}
