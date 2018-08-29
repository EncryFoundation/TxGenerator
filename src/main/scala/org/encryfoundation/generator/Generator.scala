package org.encryfoundation.generator

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import org.encryfoundation.generator.settings.GeneratorSettings
import org.encryfoundation.generator.transaction.box.Box
import scala.concurrent.ExecutionContext

case class Generator(account: Account, settings: GeneratorSettings)(implicit val system: ActorSystem,
                                                                    implicit val materializer: Materializer,
                                                                    implicit val ec: ExecutionContext) extends Actor {

  val observer: ActorRef = context
    .actorOf(Props(classOf[UtxoObserver], NetworkService(account.sourceNode), settings.network), "node-observer")

  override def receive: Receive = {
    case _ =>
  }
}

object Generator {

  case class Utxos(elts: Set[Box])
}
