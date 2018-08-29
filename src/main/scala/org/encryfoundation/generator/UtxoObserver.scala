package org.encryfoundation.generator

import akka.actor.{Actor, Cancellable}
import org.encryfoundation.generator.UtxoObserver.RequestUtxos
import org.encryfoundation.generator.settings.NetworkSettings
import org.encryfoundation.generator.transaction.box.Box
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

case class UtxoObserver(service: NetworkService, settings: NetworkSettings) extends Actor {

  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  var pool: Set[Box] = Set.empty

  val utxosRequest: Cancellable = context.system.scheduler
    .schedule(5.seconds, settings.nodePollingInterval)(fetchUtxos())

  override def receive: Receive = {
    case RequestUtxos(qty) =>
  }

  def fetchUtxos(): Unit = service
    .requestUtxos
    .map { outputs => pool = pool ++ outputs }
}

object UtxoObserver {

  case class RequestUtxos(qty: Int)
}
