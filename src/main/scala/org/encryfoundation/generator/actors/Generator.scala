package org.encryfoundation.generator.actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, Props, SupervisorStrategy}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.transaction.Pay2PubKeyAddress
import org.encryfoundation.generator.transaction.box.Box
import org.encryfoundation.generator.actors.Generator.Utxos
import org.encryfoundation.generator.actors.UtxoObserver.RequestUtxos
import org.encryfoundation.generator.transaction.Account
import org.encryfoundation.generator.GeneratorApp._
import scala.concurrent.duration._

class Generator(account: Account) extends Actor with StrictLogging {

  val observableAddress: Pay2PubKeyAddress = account.secret.publicImage.address

  val broadcaster: ActorRef = context
    .actorOf(Props(classOf[Broadcaster]), s"broadcaster-${observableAddress.address}")

  val observer: ActorRef = context
    .actorOf(Props(classOf[UtxoObserver], account.sourceNode), s"observer-${observableAddress.address}")

  val askUtxos: Cancellable = context.system.scheduler
    .schedule(5.seconds, settings.generator.askUtxoTimeFromLocalPool.seconds) {
      observer ! RequestUtxos(settings.generator.utxoQty)
      logger.info(s"Generator asked ${settings.generator.utxoQty} from local pool")
    }

  override def receive: Receive = {
    case Utxos(outputs) if outputs.nonEmpty =>
      val partitionSize: Int =
        if (outputs.size > settings.generator.partitionsQty * 2) outputs.size / settings.generator.partitionsQty
        else outputs.size
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