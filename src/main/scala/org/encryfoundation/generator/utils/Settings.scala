package org.encryfoundation.generator.utils

import scala.concurrent.duration.FiniteDuration

case class Settings(peers: List[Node],
                    influxDB: Option[InfluxDBSettings],
                    generator: GeneratorSettings,
                    boxesHolderSettings: BoxesHolderSettings,
                    transactions: TransactionsSettings,
                    network: NetworkSettings,
                    ntp: NetworkTimeProviderSettings,
                    multisig: MultisigSettings)

case class Node(explorerHost: String,
                explorerPort: Int,
                mnemonicKey: String)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int)

case class GeneratorSettings(transactionsSendingFrequency: Int)

case class BoxesHolderSettings(askingAPIFrequency: FiniteDuration,
                               rangeForAskingBoxes: Int,
                               poolSize: Int,
                               maxPoolSize: Long,
                               bloomFilterCleanupInterval: FiniteDuration,
                               bloomFilterCapacity: Long,
                               bloomFilterFailureProbability: Double)

case class TransactionsSettings(numberOfDataTxs: Int,
                                numberOfMonetaryTxs: Int,
                                numberOfMultisigTxs: Int,
                                requiredAmount: Int,
                                feeAmount: Int,
                                dataTxSize: Int,
                                numberOfCreatedDirectives: Int)

case class MultisigSettings(checkTxMinedPeriod: Int, numberOfBlocksToCheck: Int)

case class NetworkSettings(syncPacketLength: Int,
                           bindAddressHost: String,
                           bindAddressPort: Int,
                           nodeName: String,
                           appVersion: String,
                           handshakeTimeout: FiniteDuration,
                           peerForConnectionHost: String,
                           peerForConnectionPort: Int,
                           peerForConnectionApiPort: Int,
                           declaredAddressHost: String,
                           declaredAddressPort: Int)