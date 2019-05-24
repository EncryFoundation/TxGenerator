package utils

import org.encryfoundation.common.transaction.EncryAddress.Address
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.generator.modifiers.box.{AssetBox, EncryProposition}
import scala.util.Random

object Helper {

  def genAssetBox(address: Address, amount: Long = 100000, tokenIdOpt: Option[ADKey] = None): AssetBox =
    AssetBox(EncryProposition.addressLocked(address), Random.nextLong(), amount, tokenIdOpt)

  def genNAssetBoxes(number: Int, address: Address): IndexedSeq[AssetBox] = (1 to number).map(_ => genAssetBox(address))
}