package org.encryfoundation.generator.transaction

import org.encryfoundation.common.transaction.Proof
import org.encryfoundation.common.utils.TaggedTypes.ADKey
import org.encryfoundation.prismlang.compiler.CompiledContract

case class ParsedInput(key: ADKey, contract: Option[(CompiledContract, Seq[Proof])] = None)
