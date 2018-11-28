package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, PoisonPill}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.transaction.{Pay2PubKeyAddress, PubKeyLockedContract}
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.generator.actors.Worker.StartGeneration
import org.encryfoundation.generator.transaction.{EncryTransaction, Transaction}
import org.encryfoundation.generator.transaction.box.{Box, MonetaryBox}
import org.encryfoundation.generator.GeneratorApp.settings
import scorex.utils

class Worker(secret: PrivateKey25519, partition: Seq[Box], broadcaster: ActorRef) extends Actor with StrictLogging {

  val sourceAddress: Pay2PubKeyAddress = secret.publicImage.address

  override def preStart(): Unit = self ! StartGeneration

  override def receive: Receive = {
    case StartGeneration =>
      val listTxs: List[EncryTransaction] = partition.map { case output: MonetaryBox =>
        val useAmount: Long = output.amount / 4
        scala.util.Random.nextInt(3) match {
          case 0 =>
            Transaction.dataTransactionScratch(
              secret,
              settings.worker.feeAmount,
              System.currentTimeMillis(),
              Seq((output, None)),
              PubKeyLockedContract(secret.publicImage.pubKeyBytes).contract,
              useAmount - settings.worker.feeAmount,
              utils.Random.randomBytes(settings.dataDirective.dataSize)
            )
          case 1 =>
            Transaction.defaultPaymentTransaction(
              secret,
              settings.worker.feeAmount,
              System.currentTimeMillis(),
              Seq((output, None)),
              settings.recipientAddress,
              useAmount - settings.worker.feeAmount
            )
          case 3 =>
            Transaction.assetIssuingTransactionScratch(
              secret,
              settings.worker.feeAmount,
              System.currentTimeMillis(),
              Seq((output, None)),
              PubKeyLockedContract(secret.publicImage.pubKeyBytes).contract,
              useAmount - settings.worker.feeAmount,
              Option(ADKey @@ utils.Random.randomBytes(settings.dataDirective.dataSize))
            )
        }
      }.to[List]

      listTxs.foreach(tx => broadcaster ! Broadcaster.Transaction(tx))

      logger.info(s"Worker ${self.path.name} send ${listTxs.size} transactions")

      self ! PoisonPill
  }
}

object Worker {

  case object StartGeneration

}