package org.encryfoundation.generator

import java.net.InetSocketAddress
import akka.actor.{Actor, Cancellable}
import org.encryfoundation.generator.Generator.Utxos
import org.encryfoundation.generator.UtxoObserver.RequestUtxos
import org.encryfoundation.generator.network.NetworkService
import org.encryfoundation.generator.settings.NetworkSettings
import org.encryfoundation.generator.transaction.box.Box
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

case class UtxoObserver(host: InetSocketAddress,
                        network: NetworkService,
                        settings: NetworkSettings) extends Actor {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  var pool: Set[Box] = Set.empty

  val utxosRequest: Cancellable = context.system.scheduler
    .schedule(5.seconds, settings.nodePollingInterval)(fetchUtxos())

  override def receive: Receive = {
    case RequestUtxos(qty) =>
      val partition: Set[Box] = pool.take(
        if (qty < 0) pool.size
        else if (qty <= pool.size) qty
        else pool.size
      )
      pool = pool -- partition
      sender() ! Utxos(partition)
  }

  def fetchUtxos(): Unit = network
    .requestUtxos(host)
    .map { outputs => pool = pool ++ outputs }
}

object UtxoObserver {

  case class RequestUtxos(qty: Int)
}
