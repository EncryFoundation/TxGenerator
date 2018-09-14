package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, PoisonPill}
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.transaction.Pay2PubKeyAddress
import org.encryfoundation.generator.actors.Worker.StartGeneration
import org.encryfoundation.generator.transaction.{EncryTransaction, Transaction}
import org.encryfoundation.generator.transaction.box.{Box, MonetaryBox}
import org.encryfoundation.generator.GeneratorApp.{settings, system}
import org.encryfoundation.generator.actors.InfluxActor.SendTxsQty

class Worker(secret: PrivateKey25519, partition: Seq[Box], broadcaster: ActorRef) extends Actor {

  val sourceAddress: Pay2PubKeyAddress = secret.publicImage.address

  override def preStart(): Unit = self ! StartGeneration

  override def receive: Receive = {
    case StartGeneration =>
      val listTxs: List[EncryTransaction] = partition.map { case output: MonetaryBox =>
        val useAmount: Long = output.amount / 4
        Transaction.defaultPaymentTransaction(
          secret,
          settings.worker.feeAmount,
          System.currentTimeMillis(),
          Seq((output, None)),
          sourceAddress.address,
          useAmount - settings.worker.feeAmount
        )
      }.to[List]

      broadcaster ! Broadcaster.Transaction(listTxs)
      system.actorSelection("user/influxDB") ! SendTxsQty(listTxs.size)
      self ! PoisonPill
  }
}

object Worker {

  case object StartGeneration

}