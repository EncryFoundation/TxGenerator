package org.encryfoundation.generator.settings

import java.net.InetSocketAddress
import scala.concurrent.duration.FiniteDuration

case class NetworkSettings(knownPeers: List[InetSocketAddress], nodePollingInterval: FiniteDuration)
