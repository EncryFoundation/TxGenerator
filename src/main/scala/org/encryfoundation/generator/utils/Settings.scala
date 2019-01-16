package org.encryfoundation.generator.utils

case class Settings(peers: List[Node],
                    influxDB: Option[InfluxDBSettings],
                    generator: GeneratorSettings,
                    directory: String,
                    boxesHolderSettings: BoxesHolderSettings,
                    walletSettings: WalletSettings,
                    transactions: TransactionsSettings,
                    privKey: PrivKey)

case class Node(host: String, port: Int, mnemonicKey: String)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int)

case class GeneratorSettings(askBoxesHolderForBoxesPeriod: Int)

case class BoxesHolderSettings(getBoxesFromIODbPeriod: Int, periodOfCleaningPool: Int)

case class WalletSettings(password: String)

case class TransactionsSettings(numberOfDataTxs: Int,
                                numberOfMonetaryTxs: Int,
                                requiredAmount: Int,
                                feeAmount: Int,
                                dataTxSize: Int,
                                numberOfCreatedDirectives: Int)

case class PrivKey(privKey: String)