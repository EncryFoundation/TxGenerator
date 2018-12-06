package org.encryfoundation.generator.utils

case class Settings(peers: List[Node],
                    influxDB: InfluxDBSettings,
                    generator: GeneratorSettings,
                    worker: WorkerSettings,
                    directory: String,
                    boxesHolderSettings: BoxesHolderSettings,
                    walletSettings: WalletSettings)

case class Node(host: String, port: Int)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int,
                            enable: Boolean)

case class GeneratorSettings(askBoxesHolderForBoxesPeriod: Int, partitionsQty: Int)

case class WorkerSettings(feeAmount: Int, useAmountDivisor: Int)

case class BoxesHolderSettings(askBoxesFromLocalDBPeriod: Int, qtyOfAskedBoxes: Int)

case class WalletSettings(password: String)