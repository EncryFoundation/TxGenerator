package org.encryfoundation.generator

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import org.encryfoundation.generator.actors.{Generator, InfluxActor}
import org.encryfoundation.generator.transaction.Account
import org.encryfoundation.generator.utils.Settings
import scala.concurrent.ExecutionContextExecutor

object GeneratorApp extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val settings: Settings = Settings.load

  val accounts: Seq[Account] = Account.parseFromFile("/accounts.txt")

  val generators: Seq[ActorRef] = accounts.zipWithIndex
    .map { case (account, idx) =>
      system.actorOf(Props(classOf[Generator], account), s"generator-$idx")
    }

  if (settings.influxDB.enable) system.actorOf(Props[InfluxActor], "influxDB")

}