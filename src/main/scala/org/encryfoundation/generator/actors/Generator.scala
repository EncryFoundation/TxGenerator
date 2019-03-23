package org.encryfoundation.generator.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.{PrivateKey25519, Signature25519}
import org.encryfoundation.common.transaction.{Proof, PubKeyLockedContract}
import org.encryfoundation.generator.actors.BlockchainListener.{CheckTxMined, MultisigTxsInBlockchain}
import org.encryfoundation.generator.actors.BoxesHolder._
import org.encryfoundation.generator.transaction.{Contracts, EncryTransaction, Transaction}
import org.encryfoundation.generator.transaction.box.{AssetBox, Box, MonetaryBox}

import scala.concurrent.ExecutionContext.Implicits.global
import org.encryfoundation.prismlang.compiler.CompiledContract
import org.encryfoundation.prismlang.core.wrapped.BoxedValue.MultiSignatureValue
import scorex.crypto.hash.Blake2b256
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}
import org.encryfoundation.generator.utils.{NetworkService, Node, Settings}
import scorex.utils

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random
import scala.util.control.NonFatal

class Generator(settings: Settings,
                privKey: PrivateKey25519,
                nodeForLocalPrivKey: Node,
                influx: Option[ActorRef]) extends Actor with StrictLogging {

  val multisigKeys: List[PrivateKey25519] =
    if (settings.transactions.numberOfMultisigTxs > 0)
      settings
        .multisig
        .mnemonicKeys
        .map(seed => Curve25519.createKeyPair(Blake2b256.hash(seed)))
        .map(pair => PrivateKey25519(pair._1, pair._2))
    else List.empty
  var multisigBoxes: Seq[Box] = Seq.empty
  val blockchainListener: ActorRef =
    context.actorOf(Props(classOf[BlockchainListener], settings), "blockchainListener")
  val boxesHolder: ActorRef = context.system.actorOf(
      BoxesHolder.props(settings, influx, nodeForLocalPrivKey), s"boxesHolder${nodeForLocalPrivKey.host}")
  context.system.scheduler.schedule(40.seconds, settings.generator.askBoxesHolderForBoxesPeriod.seconds) {
    boxesHolder ! AskBoxesFromGenerator
    logger.info(s"Generator asked boxesHolder for new boxes.")
  }

  override def receive: Receive = {
    case BoxesForGenerator(boxes, txType) if boxes.nonEmpty || txType == 4 =>
      generateAndSendTransaction(boxes, txType)
    case MultisigTxsInBlockchain(txs) => boxesHolder ! AskBoxesForMultisigSigning(txs)
    case _ => logger.info(s"No boxes in IoDB.")
  }

  def generateAndSendTransaction(boxes: List[AssetBox], txsType: Int): Future[Unit] = Future {
    val transaction: EncryTransaction = txsType match {
      case 1 => Transaction.dataTransactionScratch(
        privKey,
        settings.transactions.feeAmount,
        System.currentTimeMillis(),
        boxes.map(_ -> None),
        PubKeyLockedContract(privKey.publicImage.pubKeyBytes).contract,
        settings.transactions.requiredAmount,
        utils.Random.randomBytes(settings.transactions.dataTxSize),
        settings.transactions.numberOfCreatedDirectives
      )
      case 2 => Transaction.defaultPaymentTransaction(
        privKey,
        settings.transactions.feeAmount,
        System.currentTimeMillis(),
        boxes.map(_ -> None),
        privKey.publicImage.address.address,
        settings.transactions.requiredAmount,
        settings.transactions.numberOfCreatedDirectives
      )
      case 3 =>
        Transaction.scriptedAssetTransactionScratch(
          privKey,
          settings.transactions.feeAmount,
          System.currentTimeMillis(),
          boxes.map(_ -> None),
          Contracts.multiSigContractScratch(multisigKeys.map(_.publicKeyBytes), settings.multisig.multisigThreshold).get,
          settings.transactions.requiredAmount,
          settings.transactions.numberOfCreatedDirectives,
          None
        )
      case 4 =>
        val compiledContract: CompiledContract = Contracts.multiSigContractScratch(multisigKeys.map(_.publicKeyBytes), settings.multisig.multisigThreshold).get
        val ts: Long = System.currentTimeMillis()
        val msgToSign: Array[Byte] = Transaction.defaultPaymentTransaction(
          privKey,
          settings.transactions.feeAmount,
          ts,
          multisigBoxes.collect {
            case b: MonetaryBox => b
          }.map(_ -> Some(compiledContract -> Seq())),
          privKey.publicImage.address.address,
          settings.transactions.requiredAmount - settings.transactions.feeAmount,
          settings.transactions.numberOfCreatedDirectives
        ).messageToSign

        val signatures: List[List[Byte]] = Random.shuffle(multisigKeys)
          .take(settings.multisig.multisigThreshold)
          .map(_.sign(msgToSign))
          .map(_.signature)
          .map(_.toList)
        val proofs: Seq[Proof] = Seq(Proof(MultiSignatureValue(signatures), Some("signature")))
        Transaction.defaultPaymentTransaction(
          privKey,
          settings.transactions.feeAmount,
          ts,
          multisigBoxes.collect {
            case b: MonetaryBox => b
          }.map(_ -> Some(compiledContract -> proofs)),
          privKey.publicImage.address.address,
          settings.transactions.requiredAmount - settings.transactions.feeAmount,
          settings.transactions.numberOfCreatedDirectives
        )
    }
    if (txsType == 3) {
      blockchainListener ! CheckTxMined(Algos.encode(transaction.id))
      multisigBoxes ++= transaction.newBoxes
    }
    if (txsType == 4) {
      blockchainListener ! CheckTxMined(Algos.encode(transaction.id))
      multisigBoxes = Seq.empty
    }
    if (txsType == 3) {
      blockchainListener ! CheckTxMined(Algos.encode(transaction.id))
      multisigBoxes ++= transaction.newBoxes
    }
    if (txsType == 4) multisigBoxes = Seq.empty
    settings.peers.foreach(NetworkService.commitTransaction(_, transaction))
    logger.info(s"Generated and sent new transaction with id: ${Algos.encode(transaction.id)}." +
      s" Tx type is: ${txsType match {
        case 1 => "DataTx"
        case 2 => "MonetaryTx"
        case 3 => "Multisig deploy"
        case 4 => "Multisig signing"
      }}")
  }.recoverWith {
    case NonFatal(th) =>
      th.printStackTrace()
      Future.failed(th)
  }

  def createKeyPair: PrivateKey25519 = {
    val (privateKey: PrivateKey, publicKey: PublicKey) = Curve25519.createKeyPair(
      Blake2b256.hash(scorex.utils.Random.randomBytes(16))
    )
    PrivateKey25519(privateKey, publicKey)
  }
}

object Generator {
  def props(settings: Settings, privKey: PrivateKey25519, nodeForLocalPrivKey: Node, influx: Option[ActorRef]): Props =
    Props(new Generator(settings, privKey, nodeForLocalPrivKey, influx))
}