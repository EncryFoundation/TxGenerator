package org.encryfoundation.generator

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContextExecutor

object GeneratorApp extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val accounts: Seq[Account] = Account.parseFromFile("/accounts.txt")

  val generators: Seq[ActorRef] = accounts.zipWithIndex
    .map { case (account, idx) => system.actorOf(Props(classOf[Generator], account), s"generator-$idx") }
}
