package org.encryfoundation.generator.wallet

import org.encryfoundation.common.Algos
import org.encryfoundation.generator.GeneratorApp.system
import org.encryfoundation.generator.actors.InfluxActor.GetAllTimeSeconds
import org.encryfoundation.generator.transaction.box.EncryBaseBox
import org.encryfoundation.generator.utils.StateModifierDeserializer
import org.iq80.leveldb.DB

case class LevelDB(db: DB) {

  import LevelDB._

  def deserializeBox(data: Array[Byte], id: Byte): Option[EncryBaseBox] =
    StateModifierDeserializer.parseBytes(data, id).toOption

  def getAll: List[EncryBaseBox] = {
    val startTime: Long = System.currentTimeMillis()
    var elementsBuffer: Seq[(Array[Byte], Array[Byte])] = Seq()
    val iterator = db.iterator()
    iterator.seekToFirst()
    system.actorSelection("/user/generator/influxDB") !
      GetAllTimeSeconds((System.currentTimeMillis() - startTime) / 1000)
    while (iterator.hasNext) {
      val nextElem = iterator.next()
      val nextKey: Array[Byte] = nextElem.getKey
      elementsBuffer =
        if (nextKey sameElements balancesKey) elementsBuffer :+ (nextElem.getKey -> nextElem.getValue)
        else elementsBuffer
    }
    system.actorSelection("/user/generator/influxDB") !
      GetAllTimeSeconds((System.currentTimeMillis() - startTime) / 1000)
    iterator.seekToLast()
    elementsBuffer.foldLeft(List[EncryBaseBox]()) { case (boxes, tuple) =>
      deserializeBox(tuple._2, tuple._1.head).map(box => boxes :+ box).getOrElse(boxes)
    }
  }
}

object LevelDB {
  val balancesKey: Array[Byte] = Algos.hash("balances")
}