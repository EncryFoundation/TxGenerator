package org.encryfoundation.generator.actors

import org.encryfoundation.common.modifiers.state.box.{EncryBaseBox, MonetaryBox}

final case class BoxesBatch(boxes: List[MonetaryBox])

object BoxesBatch { def empty: BoxesBatch = BoxesBatch(List.empty[MonetaryBox]) }
