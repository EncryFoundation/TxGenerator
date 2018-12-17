package org.encryfoundation.generator.actors

import com.typesafe.scalalogging.StrictLogging
import org.encryfoundation.common.Algos
import org.encryfoundation.generator.transaction.box.AssetBox
import scala.collection.immutable.TreeSet

case class LocalPool(encryCoinBoxes: Map[String, AssetBox] = Map(),
                     usedOutputs: TreeSet[String]          = TreeSet()) extends StrictLogging {

  def updatePool(encryCoinBoxes: List[AssetBox]): LocalPool = {
    val ECBoxesMap: Map[String, AssetBox]      = Map(encryCoinBoxes.map(k => Algos.encode(k.id) -> k): _*)
    logger.info(s"Num of ecbMap: ${ECBoxesMap.size}")
    val filteredECBoxes: Map[String, AssetBox] = ECBoxesMap.filterKeys(key => !usedOutputs.contains(key))
    logger.info(s"Num of filteredECBoxes: ${filteredECBoxes.size}")
    val newUsedBoxes: Map[String, AssetBox]    = ECBoxesMap -- filteredECBoxes.keys
    logger.info(s"Num of newUsedBoxes: ${newUsedBoxes.size}")
    LocalPool(filteredECBoxes, newUsedBoxes.keys.to[TreeSet])
  }

  def getECOutputs: (LocalPool, List[AssetBox]) = {
    val usedBoxes: TreeSet[String] = usedOutputs ++ encryCoinBoxes.keys
    (LocalPool(Map(), usedBoxes), encryCoinBoxes.values.toList)
  }

  def addUnusedECToPool(unused: List[AssetBox]): LocalPool = {
    val unusedOutputsMap                    = Map(unused.map(k => Algos.encode(k.id) -> k): _*)
    val newUsedOutputs: TreeSet[String]     = usedOutputs -- unusedOutputsMap.keys
    val newEcOutputs: Map[String, AssetBox] = encryCoinBoxes ++ unusedOutputsMap
    LocalPool(newEcOutputs, newUsedOutputs)
  }

  override def toString: String =
    s"UsedOutputsSize is: ${usedOutputs.size}. EncryCoinBoxesSize is: ${encryCoinBoxes.size}."
}