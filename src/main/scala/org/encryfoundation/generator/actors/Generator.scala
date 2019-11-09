package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.crypto.PrivateKey25519
import org.encryfoundation.common.modifiers.mempool.transaction.{Proof, PubKeyLockedContract, Transaction}
import org.encryfoundation.common.modifiers.state.box.{AssetBox, Box, EncryBaseBox, MonetaryBox}
import org.encryfoundation.common.utils.Algos
import org.encryfoundation.generator.actors.BlockchainListener.{CheckTxMined, MultisigTxsInBlockchain}
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.actors.Generator.TransactionForCommit
import org.encryfoundation.generator.modifiers.TransactionsFactory
import org.encryfoundation.generator.transaction.Contracts
import org.encryfoundation.generator.utils.{Mnemonic, Node, Settings}
import org.encryfoundation.prismlang.compiler.CompiledContract
import org.encryfoundation.prismlang.core.wrapped.BoxedValue.MultiSignatureValue
import scorex.crypto.hash.Blake2b256
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}
import scorex.utils
import scorex.utils.Random.{randomBytes => rBytes}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class Generator(settings: Settings,
                privKey: PrivateKey25519,
                nodeForLocalPrivKey: Node,
                influx: Option[ActorRef],
                networkServer: ActorRef) extends Actor with StrictLogging {

  val boxesHolder: ActorRef = context.system.actorOf(
      BoxesHolder.props(settings, influx, nodeForLocalPrivKey), s"boxesHolder${nodeForLocalPrivKey.explorerHost}")
  context.system.scheduler.schedule(10.seconds, settings.generator.transactionsSendingFrequency.seconds) {
    boxesHolder ! AskBoxesFromGenerator
    logger.info(s"Generator asked boxesHolder for new boxes.")
  }

  val multisigKeys: Seq[PrivateKey25519] = if (settings.multisig.mnemonicKeys.size >= 3)
      settings.multisig.mnemonicKeys
        .take(3)
        .map(Some(_))
        .map(Mnemonic.createPrivKey)
    else
      (1 to 3)
        .map(_ => Curve25519.createKeyPair(rBytes()))
        .map(pair => PrivateKey25519(pair._1, pair._2))

  var multisigBoxes: Map[String, Seq[EncryBaseBox]] = Map.empty
  val blockchainListener: ActorRef =
    context.actorOf(Props(classOf[BlockchainListener], settings), "blockchainListener")

  override def receive: Receive = {
    case BoxesForGenerator(boxes, txType, None) if boxes.nonEmpty =>
      generateAndSendTransaction(boxes, txType)
    case BoxesForGenerator(boxes, txType, Some(forTx)) if txType == 4 && multisigBoxes.get(forTx).exists(_.nonEmpty) =>
      generateAndSendTransaction(boxes, txType, Some(forTx))
    case MultisigTxsInBlockchain(txs) => boxesHolder ! AskBoxesForMultisigSigning(txs)
    case _ =>
  }

  def generateAndSendTransaction(boxes: List[AssetBox], txsType: Int, forTx: Option[String] = None): Future[Unit] = Future {
    val transaction: Transaction = txsType match {
      case 1 => TransactionsFactory.dataTransactionScratch(
        privKey,
        settings.transactions.feeAmount,
        System.currentTimeMillis(),
        boxes.map(_ -> None),
        PubKeyLockedContract(privKey.publicImage.pubKeyBytes).contract,
        settings.transactions.requiredAmount,
        utils.Random.randomBytes(settings.transactions.dataTxSize),
        settings.transactions.numberOfCreatedDirectives
      )
      case 2 => TransactionsFactory.defaultPaymentTransaction(
        privKey,
        settings.transactions.feeAmount,
        System.currentTimeMillis(),
        boxes.map(_ -> None),
        privKey.publicImage.address.address,
        settings.transactions.requiredAmount,
        settings.transactions.numberOfCreatedDirectives
      )
      case 3 =>
        val contract = Contracts.multiSigContractScratch(multisigKeys.map(_.publicKeyBytes)).get

        TransactionsFactory.scriptedAssetTransactionScratch(
          privKey,
          settings.transactions.feeAmount,
          System.currentTimeMillis(),
          boxes.map(_ -> None),
          contract,
          settings.transactions.requiredAmount,
          settings.transactions.numberOfCreatedDirectives,
          None
        )
      case 4 if forTx.isDefined =>
        val compiledContract: CompiledContract = Contracts.multiSigContractScratch(multisigKeys.map(_.publicKeyBytes)).get
        val ts: Long = System.currentTimeMillis()

        val txWithoutProofs: Transaction = TransactionsFactory.defaultPaymentTransactionWithoutRandom(
          privKey,
          settings.transactions.feeAmount,
          ts,
          multisigBoxes(forTx.get).collect {
            case b: MonetaryBox => b
          }.map(_ -> Some(compiledContract -> Seq())) match {
            case init :+ last => init :+ last._1 -> None
          },
          privKey.publicImage.address.address,
          settings.transactions.requiredAmount - settings.transactions.feeAmount,
          settings.transactions.numberOfCreatedDirectives
        )

        val signatures: List[List[Byte]] = Random.shuffle(multisigKeys)
          .take(2)
          .map(_.sign(txWithoutProofs.messageToSign))
          .map(_.signature)
          .map(_.toList)
          .toList

        val proofs: Seq[Proof] = Seq(Proof(MultiSignatureValue(signatures), Some("signature")))
        TransactionsFactory.defaultPaymentTransactionWithoutRandom(
          privKey,
          settings.transactions.feeAmount,
          ts,
          multisigBoxes(forTx.get).collect {
            case b: MonetaryBox => b
          }.map(_ -> Some(compiledContract -> proofs)) match {
            case init :+ last => init :+ last._1 -> None
          },
          privKey.publicImage.address.address,
          settings.transactions.requiredAmount - settings.transactions.feeAmount,
          settings.transactions.numberOfCreatedDirectives
        )
    }
    if (txsType == 3) {
      blockchainListener ! CheckTxMined(Algos.encode(transaction.id))
      multisigBoxes = multisigBoxes.updated(Algos.encode(transaction.id), transaction.newBoxes.toSeq)
    }
    if (txsType == 4) {
      blockchainListener ! CheckTxMined(Algos.encode(transaction.id))
      multisigBoxes = multisigBoxes - Algos.encode(transaction.id)
    }

    logger.info(s"Commit tx ${Algos.encode(transaction.id)} with type: ${txsType match {
      case 1 => "DataTx"
      case 2 => "MonetaryTx"
      case 3 => "Multisig deploy"
      case 4 => "Multisig signing"
    }}")
    networkServer ! TransactionForCommit(transaction)
  }

  def createKeyPair: PrivateKey25519 = {
    val (privateKey: PrivateKey, publicKey: PublicKey) = Curve25519.createKeyPair(
      Blake2b256.hash(scorex.utils.Random.randomBytes(16))
    )
    PrivateKey25519(privateKey, publicKey)
  }
}

object Generator {

  case class TransactionForCommit(tx: Transaction)

  def props(settings: Settings,
            privKey: PrivateKey25519,
            nodeForLocalPrivKey: Node,
            influx: Option[ActorRef],
            networkServer: ActorRef): Props =
    Props(new Generator(settings, privKey, nodeForLocalPrivKey, influx, networkServer))
}