package org.encryfoundation.generator

import java.net.InetSocketAddress

import akka.actor.Actor
import org.encryfoundation.generator.Generator.RequestUtxos
import org.encryfoundation.generator.transaction.box.Box

case class NodeObserver(node: InetSocketAddress) extends Actor {

  var utxoPool: List[Box] = List.empty

  override def receive: Receive = {
    case RequestUtxos(qty) =>
  }
}
