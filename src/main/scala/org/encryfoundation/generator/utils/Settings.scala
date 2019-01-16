package org.encryfoundation.generator.utils

case class Settings(peers: List[Node],
                    influxDB: Option[InfluxDBSettings],
                    generator: GeneratorSettings,
                    boxesHolderSettings: BoxesHolderSettings,
                    transactions: TransactionsSettings)

case class Node(host: String, port: Int, mnemonicKey: String)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int)

case class GeneratorSettings(askBoxesHolderForBoxesPeriod: Int)

case class BoxesHolderSettings(getBoxesFromApi: Int, periodOfCleaningPool: Int)

case class TransactionsSettings(numberOfDataTxs: Int,
                                numberOfMonetaryTxs: Int,
                                requiredAmount: Int,
                                feeAmount: Int,
                                dataTxSize: Int,
                                numberOfCreatedDirectives: Int)