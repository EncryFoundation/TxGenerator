package org.encryfoundation.generator.Actors

import akka.actor.Actor
import org.encryfoundation.generator.GeneratorApp.settings
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.utils.NetworkService

class Broadcaster extends Actor {

  override def receive: Receive = {
    case Broadcaster.Transaction(tx) => settings.peers.foreach { NetworkService.commitTransaction(_, tx) }
  }
}

object Broadcaster {

  case class Transaction(tx: EncryTransaction)
}
