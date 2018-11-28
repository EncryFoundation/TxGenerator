package org.encryfoundation.generator.actors

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.GeneratorApp.settings
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.utils.NetworkService
import scala.language.postfixOps

class Broadcaster extends Actor with StrictLogging {

  override def receive: Receive = {
    case Broadcaster.Transaction(tx) => settings.peers.foreach(NetworkService.commitTransaction(_, tx))
      logger.info(s"Broadcaster send $tx transactions to the nodes") }
}

object Broadcaster {
  case class Transaction(txs: EncryTransaction)
}