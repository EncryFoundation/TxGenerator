package TxGen

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContextExecutor
import utils.Settings
import com.typesafe.scalalogging.StrictLogging
import TxGen.stats.InfluxActor

object TxGenerator extends App with StrictLogging {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  logger.info("hello")

  val settings: Settings = Settings.load

  if(settings.influxDB.enable) system.actorOf(Props[InfluxActor], "influxDB")

}
