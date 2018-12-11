package org.encryfoundation.generator.utils

case class Settings(peers: List[Node],
                    influxDB: Option[InfluxDBSettings],
                    generator: GeneratorSettings,
                    directory: String,
                    boxesHolderSettings: BoxesHolderSettings,
                    walletSettings: WalletSettings,
                    transactions: TransactionsSettings)

case class Node(host: String, port: Int)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int)

case class GeneratorSettings(askBoxesHolderForBoxesPeriod: Int)

case class BoxesHolderSettings(askBoxesFromLocalDBPeriod: Int)

case class WalletSettings(password: String)

case class TransactionsSettings(numberOfDataTxs: Int,
                                totalNumberOfTxs: Int,
                                numberOfRequestedBoxes: Int,
                                requiredAmount: Int,
                                feeAmount: Int,
                                dataTx: String,
                                paymentTx: String,
                                dataTxSize: Int,
                                numberOfCreatedDirectives: Int)