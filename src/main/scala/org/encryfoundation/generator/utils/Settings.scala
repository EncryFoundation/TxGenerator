package org.encryfoundation.generator.utils

import scala.concurrent.duration.FiniteDuration

case class Settings(peers: List[Node],
                    influxDB: Option[InfluxDBSettings],
                    generator: GeneratorSettings,
                    boxesHolderSettings: BoxesHolderSettings,
                    transactions: TransactionsSettings)

case class Node(explorerHost: String,
                explorerPort: Int,
                nodeHost: String,
                nodePort: Int,
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
                                requiredAmount: Int,
                                feeAmount: Int,
                                dataTxSize: Int,
                                numberOfCreatedDirectives: Int)