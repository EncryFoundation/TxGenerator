package org.encryfoundation.generator.actors

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.generator.actors.BlockchainListener.{CheckTxMined, MultisigTxsInBlockchain, TimeToCheck}
import org.encryfoundation.generator.utils.{NetworkService, Settings}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

class BlockchainListener(settings: Settings) extends Actor with StrictLogging {

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def receive: Receive = operating(Vector.empty)

  override def preStart(): Unit =
    context.system.scheduler.schedule(settings.multisig.checkTxMinedPeriod.seconds, settings.multisig.checkTxMinedPeriod.seconds) {
      self ! TimeToCheck
    }

  def operating(txsToCheck: Vector[String]): Receive = {
    case CheckTxMined(id) => context.become(operating(txsToCheck :+ id))
    case TimeToCheck if txsToCheck.nonEmpty =>
      Future
        .sequence(settings.peers.map(NetworkService.checkTxsInBlockchain(_, txsToCheck, settings.multisig.numberOfBlocksToCheck)))
        .map(_.flatten.toSet)
        .foreach { txs =>
          if (txs.nonEmpty) {
            logger.info(s"Multisig txs ${txs.mkString(", ")} are in blockchain now")
            context.parent ! MultisigTxsInBlockchain(txs)
            self ! MultisigTxsInBlockchain(txs)
          }
        }
    case TimeToCheck =>
    case MultisigTxsInBlockchain(txs) => context.become(operating(txsToCheck.diff(txs.toSeq)))
  }

}

object BlockchainListener {
  case class CheckTxMined(txId: String)
  case class MultisigTxsInBlockchain(ids: Set[String])
  case object TimeToCheck
}