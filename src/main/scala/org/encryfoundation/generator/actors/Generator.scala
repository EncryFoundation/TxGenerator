package org.encryfoundation.generator.actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, Props, SupervisorStrategy}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.transaction.Pay2PubKeyAddress
import org.encryfoundation.generator.actors.BoxesHolder.{AskBoxesFromGenerator, BoxesAnswerToGenerator}
import scala.concurrent.ExecutionContext.Implicits.global
import org.encryfoundation.generator.utils.Settings
import org.encryfoundation.generator.wallet.WalletStorageReader
import scala.concurrent.duration._

class Generator(settings: Settings,
                privKey: PrivateKey25519,
                walletStorageReader: WalletStorageReader) extends Actor with StrictLogging {

  val observableAddress: Pay2PubKeyAddress = privKey.publicImage.address
  val broadcaster: ActorRef =
    context.actorOf(Broadcaster.props(settings), s"broadcaster-${observableAddress.address}")
  val boxesHolder: ActorRef =
    context.system.actorOf(BoxesHolder.props(settings, walletStorageReader), "boxesHolder")
  val getLocalUtxos: Cancellable =
    context.system.scheduler.schedule(5.seconds, settings.generator.askBoxesHolderForBoxesPeriod.seconds) {
      boxesHolder ! AskBoxesFromGenerator
      logger.info(s"Generator asked boxesHolder for new boxes.")
    }

  override def receive: Receive = {
    case BoxesAnswerToGenerator(boxes) if boxes.nonEmpty =>
      val partitionSize: Int =
        if (boxes.size > settings.generator.partitionsQty * 2) boxes.size / settings.generator.partitionsQty
        else boxes.size
      boxes.sliding(partitionSize, partitionSize).foreach { partition =>
        context.actorOf(Worker.props(privKey, partition, broadcaster, settings))
      }
  }

  override def supervisorStrategy: SupervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 4, withinTimeRange = 30.seconds) {
      case _ => Restart
    }
}

object Generator {
  def props(settings: Settings, privKey: PrivateKey25519, walletStorageReader: WalletStorageReader): Props =
    Props(new Generator(settings, privKey, walletStorageReader))
}