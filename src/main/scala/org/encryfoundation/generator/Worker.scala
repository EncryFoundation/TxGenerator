package org.encryfoundation.generator

import akka.actor.{Actor, ActorRef, PoisonPill}
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.transaction.Pay2PubKeyAddress
import org.encryfoundation.generator.Worker.StartGeneration
import org.encryfoundation.generator.network.Broadcaster
import org.encryfoundation.generator.transaction.Transaction
import org.encryfoundation.generator.transaction.box.{Box, MonetaryBox}

case class Worker(secret: PrivateKey25519,
                  partition: Set[Box],
                  broadcaster: ActorRef) extends Actor {

  val sourceAddress: Pay2PubKeyAddress = secret.publicImage.address

  override def preStart(): Unit = self ! StartGeneration

  override def receive: Receive = {
    case StartGeneration =>
      partition.foreach { case output: MonetaryBox =>
        val useAmount: Long = output.amount / 4
        val feeAmount: Long = 101
        broadcaster ! Broadcaster.Transaction(
          Transaction.defaultPaymentTransaction(
            secret,
            feeAmount,
            System.currentTimeMillis(),
            Seq((output, None)),
            sourceAddress.address,
            useAmount - feeAmount
          )
        )
      }
      self ! PoisonPill
  }
}

object Worker {

  case object StartGeneration
}
