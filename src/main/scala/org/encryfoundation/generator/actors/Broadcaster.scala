package org.encryfoundation.generator.actors

import akka.actor.{Actor, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.transaction.EncryTransaction
import org.encryfoundation.generator.utils.{NetworkService, Settings}
import scala.language.postfixOps

class Broadcaster(settings: Settings) extends Actor with StrictLogging {

  override def receive: Receive = {
    case Broadcaster.Transaction(tx) => settings.peers.foreach(NetworkService.commitTransaction(_, tx))
      logger.info(s"Broadcaster send ${Algos.encode(tx.id)} transaction to the nodes.") }
}

object Broadcaster {
  def props(settings: Settings): Props = Props(new Broadcaster(settings))
  case class Transaction(txs: EncryTransaction)
}