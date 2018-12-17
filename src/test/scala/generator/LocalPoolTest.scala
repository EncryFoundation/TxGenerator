package generator

import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PublicKey25519
import org.encryfoundation.generator.actors.LocalPool
import org.encryfoundation.generator.transaction.box.{AssetBox, MonetaryBox}
import org.scalatest.{Matchers, PropSpec}
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}
import utils.Helper._
import scala.collection.immutable.TreeSet

class LocalPoolTest extends PropSpec with Matchers {

  val keys: (PrivateKey, PublicKey)         = Curve25519.createKeyPair
  val address: String                       = PublicKey25519(keys._2).address.address
  var pool: LocalPool                       = LocalPool()
  val sameOutputs: List[AssetBox]           = genNAssetBoxes(1000, address).toList
  val sameOutputsMap: Map[String, AssetBox] = Map(sameOutputs.map(k => Algos.encode(k.id) -> k): _*)

  property("Pool should updates correctly while updating") {

    pool = pool.updatePool(sameOutputs)

    pool.encryCoinBoxes.keys.toList shouldEqual sameOutputsMap.keys.toList

    val boxes: (LocalPool, List[AssetBox]) = pool.getECOutputs

    pool = boxes._1

    pool = pool.updatePool(sameOutputs)

    pool.usedOutputs.map(x => sameOutputsMap.contains(x)) shouldEqual TreeSet(true)

    pool.encryCoinBoxes.values.toList shouldEqual List()

    val sameOutputsNew: List[AssetBox] = genNAssetBoxes(1000, address).toList

    val sameOutputsMapNew: Map[String, AssetBox] = Map(sameOutputsNew.map(k => Algos.encode(k.id) -> k): _*)

    pool = pool.updatePool(sameOutputsNew)

    pool.encryCoinBoxes.keys.toList shouldEqual sameOutputsMapNew.keys.toList

    val getOutputs: (LocalPool, List[AssetBox]) = pool.getECOutputs

    pool = getOutputs._1

    pool.usedOutputs.map(x => (sameOutputsMap ++ sameOutputsMapNew).contains(x) ) shouldEqual TreeSet(true)

    pool = pool.addUnusedECToPool(getOutputs._2)

    pool.usedOutputs.map(x => sameOutputsMap.contains(x)) shouldEqual TreeSet()

    pool.encryCoinBoxes.keys.toList shouldEqual sameOutputsMapNew.keys.toList
  }
}