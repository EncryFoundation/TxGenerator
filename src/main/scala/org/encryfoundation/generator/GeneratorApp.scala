package org.encryfoundation.generator

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.encryfoundation.generator.settings.GeneratorSettings
import org.encryfoundation.generator.settings.GeneratorSettings._
import scala.concurrent.ExecutionContextExecutor

object GeneratorApp extends App {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val settings: GeneratorSettings = ConfigFactory.load("local.conf")
    .withFallback(ConfigFactory.load()).as[GeneratorSettings]("encry")

  val accounts: Seq[Account] = Account.parseFromFile("/accounts.txt")

  val generators: Seq[ActorRef] = accounts.zipWithIndex
    .map { case (account, idx) =>
      system.actorOf(Props(classOf[Generator], account, settings, system, materializer, ec), s"generator-$idx") }
}
