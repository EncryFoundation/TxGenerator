package org.encryfoundation.generator.actors

import akka.actor.Actor
import org.encryfoundation.generator.GeneratorApp.settings
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.utils.NetworkService
import org.encryfoundation.generator.GeneratorApp._
import org.encryfoundation.generator.actors.InfluxActor.SendTxsQty
import scala.concurrent.duration._
import scala.language.postfixOps

class Broadcaster extends Actor {

  var sendTxs: Int = 0

  system.scheduler.schedule(5 second, 60 second) {
    system.actorSelection("user/influxDB") ! SendTxsQty(sendTxs)
    sendTxs = 0
  }

  override def receive: Receive = {
    case Broadcaster.Transaction(tx) => settings.peers.foreach {
      sendTxs += 1
      NetworkService.commitTransaction(_, tx)
    }
  }
}

object Broadcaster {

  case class Transaction(txs: List[EncryTransaction])

}
