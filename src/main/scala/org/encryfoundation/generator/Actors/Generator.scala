package org.encryfoundation.generator.Actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, Props, SupervisorStrategy}
import org.encryfoundation.common.transaction.Pay2PubKeyAddress
import org.encryfoundation.generator.transaction.box.Box
import org.encryfoundation.generator.Actors.Generator.Utxos
import org.encryfoundation.generator.Actors.UtxoObserver.RequestUtxos
import org.encryfoundation.generator.transaction.Account
import org.encryfoundation.generator.GeneratorApp._
import scala.concurrent.duration._

class Generator(account: Account) extends Actor {

  val observableAddress: Pay2PubKeyAddress = account.secret.publicImage.address

  val broadcaster: ActorRef = context
    .actorOf(Props(classOf[Broadcaster]), s"broadcaster-${observableAddress.address}")

  val observer: ActorRef = context
    .actorOf(Props(classOf[UtxoObserver], account.sourceNode), s"observer-${observableAddress.address}")

  val askUtxos: Cancellable = context.system.scheduler
    .schedule(5.seconds, 5.seconds) {
      observer ! RequestUtxos(-1)
    }

  override def receive: Receive = {
    case Utxos(outputs) if outputs.nonEmpty =>
      val partitionsQty: Int = 4
      val partitionSize: Int = if (outputs.size > partitionsQty * 2) outputs.size / partitionsQty else outputs.size
      outputs.sliding(partitionSize, partitionSize).foreach { partition =>
        context.actorOf(Props(classOf[Worker], account.secret, partition, broadcaster))
      }
  }

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 4, withinTimeRange = 30.seconds) {
      case _ => Restart
    }
}

object Generator {

  case class Utxos(outputs: Seq[Box])

}
