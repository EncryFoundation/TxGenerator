package org.encryfoundation.generator.utils

case class Settings(peers: List[Node],
                    accountSettings: List[AccountsSettings],
                    influxDB: InfluxDBSettings,
                    generator: GeneratorSettings,
                    worker: WorkerSettings,
                    directory: String,
                    boxesHolderSettings: BoxesHolderSettings)

case class Node(host: String, port: Int)

case class AccountsSettings(publicKey: String, privateKey: String, node: Node)

case class InfluxDBSettings(url: String,
                            login: String,
                            password: String,
                            udpPort: Int,
                            enable: Boolean)

case class GeneratorSettings(askBoxesHolderForBoxesPeriod: Int, partitionsQty: Int)

case class WorkerSettings(feeAmount: Int)

case class BoxesHolderSettings(askBoxesFromLocalDBPeriod: Int, qtyOfAskedBoxes: Int)