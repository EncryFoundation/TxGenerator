package org.encryfoundation.generator.wallet

import com.typesafe.scalalogging.StrictLogging
import io.iohk.iodb.{ByteArrayWrapper, Store}
import org.encryfoundation.common.Algos
import org.encryfoundation.common.crypto.PublicKey25519
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.generator.transaction.box.EncryBaseBox
import org.encryfoundation.generator.utils.StateModifierDeserializer

case class WalletStorage(store: Store, publicKeys: Set[PublicKey25519]) extends StrictLogging {

  import WalletStorage._

  def getBoxById(id: ADKey): Option[EncryBaseBox] = store.get(keyByBoxId(id))
    .flatMap(d => StateModifierDeserializer.parseBytes(d.data, id.head).toOption)

  def allBoxes: Seq[EncryBaseBox] = store.getAll
    .foldLeft(Seq[EncryBaseBox]()) {
      case (acc, id) if id._1 != balancesKey => getBoxById(ADKey @@ id._1.data).map(bx => acc :+ bx).getOrElse(acc)
      case (acc, _) => acc
    }
}

object WalletStorage {

  val balancesKey: ByteArrayWrapper = ByteArrayWrapper(Algos.hash("balances"))

  def keyByBoxId(id: ADKey): ByteArrayWrapper = ByteArrayWrapper(id)
}