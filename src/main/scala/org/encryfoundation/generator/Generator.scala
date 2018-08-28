package org.encryfoundation.generator

import akka.actor.{Actor, ActorRef, Props}

case class Generator(account: Account) extends Actor {

  val observer: ActorRef = context.actorOf(Props(classOf[NodeObserver], account.sourceNode), "node-observer")

  override def receive: Receive = {
    case _ =>
  }
}

object Generator {

  case class RequestUtxos(qty: Int)
}
