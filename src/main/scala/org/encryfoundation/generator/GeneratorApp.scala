package org.encryfoundation.generator

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import org.encryfoundation.generator.actors.{Generator, InfluxActor}
import org.encryfoundation.generator.transaction.Account
import org.encryfoundation.generator.utils.Settings
import scala.concurrent.ExecutionContextExecutor

object GeneratorApp extends App {

  implicit lazy val system: ActorSystem = ActorSystem()
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  implicit lazy val ec: ExecutionContextExecutor = system.dispatcher

  lazy val settings: Settings = Settings.load

  val generators: Seq[ActorRef] = Account.parseFromSettings(settings.accountSettings).zipWithIndex
    .map { case (account, idx) =>
      system.actorOf(Props(classOf[Generator], account), s"generator-$idx")
    }

  if (settings.influxDB.enable) system.actorOf(Props[InfluxActor], "influxDB")

}