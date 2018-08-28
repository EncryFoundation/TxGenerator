package transaction.box

import org.encryfoundation.common.Algos
import org.encryfoundation.common.utils.TaggedTypes.ADKey

abstract class EncryBoxStateChangeOperation

case class Removal(boxId: ADKey) extends EncryBoxStateChangeOperation {

  override def toString: String = s"Removal(id: ${Algos.encode(boxId)})"
}

case class Insertion(box: Box) extends EncryBoxStateChangeOperation {

  override def toString: String = s"Insertion(id: ${Algos.encode(box.id)})"
}

case class EncryBoxStateChanges(operations: Seq[EncryBoxStateChangeOperation]){

  def toAppend: Seq[EncryBoxStateChangeOperation] = operations.filter(_.isInstanceOf[Insertion])

  def toRemove: Seq[EncryBoxStateChangeOperation] = operations.filter(_.isInstanceOf[Removal])
}
