package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.transaction.{Pay2PubKeyAddress, PubKeyLockedContract}
import org.encryfoundation.generator.actors.Worker.StartGeneration
import org.encryfoundation.generator.transaction.{EncryTransaction, Transaction}
import org.encryfoundation.generator.transaction.box.{Box, MonetaryBox}
import org.encryfoundation.generator.utils.Settings
import scorex.utils

class Worker(secret: PrivateKey25519,
             partition: Seq[Box],
             broadcaster: ActorRef,
             settings: Settings) extends Actor with StrictLogging {

  val sourceAddress: Pay2PubKeyAddress = secret.publicImage.address

  override def preStart(): Unit = self ! StartGeneration

  override def receive: Receive = {
    case StartGeneration =>
      val listTxs: List[EncryTransaction] = partition.map { case output: MonetaryBox =>
        val useAmount: Long = output.amount / settings.worker.useAmountDivisor

        scala.util.Random.nextInt(2) match {
          case 0 =>
            Transaction.dataTransactionScratch(
              secret,
              settings.worker.feeAmount,
              System.currentTimeMillis(),
              Seq((output, None)),
              PubKeyLockedContract(secret.publicImage.pubKeyBytes).contract,
              useAmount - settings.worker.feeAmount,
              utils.Random.randomBytes(1000)
            )
          case 1 =>
            Transaction.defaultPaymentTransaction(
              secret,
              settings.worker.feeAmount,
              System.currentTimeMillis(),
              Seq((output, None)),
              sourceAddress.address,
              useAmount - settings.worker.feeAmount
            )
        }
      }.to[List]

      listTxs.foreach(tx => broadcaster ! Broadcaster.Transaction(tx))

      logger.info(s"Worker ${self.path.name} send ${listTxs.size} transactions")

      self ! PoisonPill
  }
}

object Worker {
  def props(secret: PrivateKey25519, partition: Seq[Box], broadcaster: ActorRef, settings: Settings): Props =
    Props(new Worker(secret, partition, broadcaster, settings))

  case object StartGeneration
}