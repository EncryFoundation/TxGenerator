package org.encryfoundation.generator.utils

case class Settings(peers: List[Node],
                    influxDB: Option[InfluxDBSettings],
                    generator: GeneratorSettings,
                    boxesHolderSettings: BoxesHolderSettings,
                    transactions: TransactionsSettings,
                    multisig: MultisigSettings)

case class Node(host: String, port: Int, mnemonicKey: String)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int)

case class GeneratorSettings(askBoxesHolderForBoxesPeriod: Int)

case class BoxesHolderSettings(getBoxesFromApi: Int, periodOfCleaningPool: Int)

case class TransactionsSettings(numberOfDataTxs: Int,
                                numberOfMonetaryTxs: Int,
                                numberOfMultisigTxs: Int,
                                requiredAmount: Int,
                                feeAmount: Int,
                                dataTxSize: Int,
                                numberOfCreatedDirectives: Int)

case class MultisigSettings(multisigThreshold: Int,
                            mnemonicKeys: List[String],
                            checkTxMinedPeriod: Int,
                            numberOfBlocksToCheck: Int)