package org.encryfoundation.generator.actors

import org.encryfoundation.common.modifiers.state.box.AssetBox

final case class BoxesBatch(boxes: List[AssetBox])

object BoxesBatch {
  def empty: BoxesBatch = BoxesBatch(List.empty[AssetBox])

}
