package org.encryfoundation.generator

import akka.actor.{Actor, ActorRef, ActorSystem, Cancellable, Props}
import akka.stream.Materializer
import org.encryfoundation.generator.Generator.Utxos
import org.encryfoundation.generator.network.UtxoObserver.RequestUtxos
import org.encryfoundation.generator.network.{Broadcaster, NetworkService, UtxoObserver}
import org.encryfoundation.generator.settings.GeneratorSettings
import org.encryfoundation.generator.transaction.box.Box
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class Generator(account: Account,
                     settings: GeneratorSettings)
                    (implicit val system: ActorSystem,
                     implicit val materializer: Materializer,
                     implicit val ec: ExecutionContext) extends Actor {

  val network: NetworkService = NetworkService()

  val broadcaster: ActorRef = system.actorOf(Props(classOf[Broadcaster], network, settings.network))

  val observer: ActorRef = context
    .actorOf(Props(classOf[UtxoObserver], account.sourceNode, network, settings.network), "node-observer")

  val askUtxos: Cancellable = context.system.scheduler
    .schedule(5.seconds, 5.seconds) { observer ! RequestUtxos(-1) }

  override def receive: Receive = {
    case Utxos(outputs) if outputs.nonEmpty =>
      val partitionsQty: Int = 4
      val partitionSize: Int = if (outputs.size > partitionsQty * 2) outputs.size / partitionsQty else outputs.size
      outputs.sliding(partitionSize, partitionSize).foreach { partition =>
        context.actorOf(Props(classOf[Worker], account.secret, partition, broadcaster))
      }
  }
}

object Generator {

  case class Utxos(outputs: Seq[Box])
}
