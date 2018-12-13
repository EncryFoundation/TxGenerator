package generator

import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PublicKey25519
import org.encryfoundation.generator.actors.LocalPool
import org.encryfoundation.generator.transaction.box.AssetBox
import org.scalatest.{Matchers, PropSpec}
import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey}
import utils.Helper._
import scala.collection.immutable.TreeSet

class LocalPoolTest extends PropSpec with Matchers {

  val keys: (PrivateKey, PublicKey) = Curve25519.createKeyPair
  val address: String               = PublicKey25519(keys._2).address.address
  var pool: LocalPool               = LocalPool()
  val sameOutputs: List[AssetBox]   = genNAssetBoxes(1000, address).toList
  val sameOutputsMap: Map[String, AssetBox] = Map(sameOutputs.map(k => Algos.encode(k.id) -> k): _*)

  property("Pool should updates correctly while updating") {
    pool = pool.updatePool(sameOutputs)
    val a = pool.getOutputs(1000)
    pool = a._1
    pool = pool.updatePool(sameOutputs)
    pool.usedOutputs.map(x => sameOutputsMap.contains(x)) shouldEqual TreeSet(true)
    pool.freeOutputs.values.toList shouldEqual List()
  }
}