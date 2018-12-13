package org.encryfoundation.generator.actors

import org.encryfoundation.common.Algos
import org.encryfoundation.generator.transaction.box.MonetaryBox
import scala.collection.immutable.TreeSet

case class LocalPool(freeOutputs: Map[String, MonetaryBox] = Map(), usedOutputs: TreeSet[String] = TreeSet()) {

  def updatePool(newOutputs: List[MonetaryBox]): LocalPool = {
    val newOutputsMap: Map[String, MonetaryBox]  = Map(newOutputs.map(k => Algos.encode(k.id) -> k): _*)
    val newFreeOutputs: Map[String, MonetaryBox] = newOutputsMap.filterKeys(k => !usedOutputs.contains(k))
    val newUsedOutputs: Map[String, MonetaryBox] = newOutputsMap -- newFreeOutputs.keys
    LocalPool(newFreeOutputs, newUsedOutputs.keys.to[TreeSet])
  }

  def getOutputs(qty: Int): (LocalPool, List[MonetaryBox]) = {
    val returningOutputs: Map[String, MonetaryBox] = freeOutputs.take(qty)
    val newFreeOutputs: Map[String, MonetaryBox]   = freeOutputs -- returningOutputs.keys
    val newUsedOutputs: TreeSet[String]            = usedOutputs ++ returningOutputs.keys
    (LocalPool(newFreeOutputs, newUsedOutputs), returningOutputs.values.toList)
  }

  def addUnusedToPool(unused: List[MonetaryBox]): LocalPool = {
    val unusedOutputsMap                         = Map(unused.map(k => Algos.encode(k.id) -> k): _*)
    val newUsedOutputs: TreeSet[String]          = usedOutputs -- unusedOutputsMap.keys
    val newFreeOutputs: Map[String, MonetaryBox] = freeOutputs ++ unusedOutputsMap
    LocalPool(newFreeOutputs, newUsedOutputs)
  }

  override def toString: String = s"Current freeOutputsSize is: ${freeOutputs.size}. " +
    s"Current usedOutputsSize is: ${usedOutputs.size}."
}