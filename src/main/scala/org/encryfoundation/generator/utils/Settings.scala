package org.encryfoundation.generator.utils

import scala.concurrent.duration.FiniteDuration

case class Settings(peer: PeerForConnection,
                    influxDB: Option[InfluxDBSettings],
                    generator: GeneratorSettings,
                    boxesHolderSettings: BoxesHolderSettings,
                    transactions: TransactionsSettings,
                    network: NetworkSettings,
                    ntp: NetworkTimeProviderSettings)

final case class PeerForConnection(peerHost: String,
                                   peerPort: Int,
                                   peerApiPort: Int,
                                   mnemonicKey: String,
                                   explorerHost: String,
                                   explorerPort: Int)

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
                                requiredAmount: Int,
                                feeAmount: Int,
                                dataTxSize: Int,
                                numberOfCreatedDirectives: Int)

case class NetworkSettings(syncPacketLength: Int,
                           bindAddressHost: String,
                           bindAddressPort: Int,
                           declaredAddressHost: String,
                           declaredAddressPort: Int,
                           nodeName: String,
                           appVersion: String,
                           handshakeTimeout: FiniteDuration)