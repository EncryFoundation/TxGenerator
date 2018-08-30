package org.encryfoundation.generator.network

import akka.actor.Actor
import org.encryfoundation.generator.settings.NetworkSettings
import org.encryfoundation.generator.transaction.EncryTransaction

class Broadcaster(network: NetworkService, settings: NetworkSettings) extends Actor {

  override def receive: Receive = {
    case Broadcaster.Transaction(tx) =>
      settings.knownPeers.foreach { network.commitTransaction(_, tx) }
  }
}

object Broadcaster {

  case class Transaction(tx: EncryTransaction)
}
