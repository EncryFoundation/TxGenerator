package org.encryfoundation.generator.utils

import supertagged.TaggedType

object CoreTaggedTypes {

  object ModifierTypeId extends TaggedType[Byte]

  object ModifierId extends TaggedType[Array[Byte]]

  type ModifierTypeId = ModifierTypeId.Type

  type ModifierId = ModifierId.Type
}