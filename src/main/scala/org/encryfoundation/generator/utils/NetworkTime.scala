package org.encryfoundation.generator.utils

import java.net.InetAddress
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.net.ntp.{NTPUDPClient, TimeInfo}
import org.encryfoundation.generator.utils.NetworkTime.Time
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Left
import scala.util.control.NonFatal

object NetworkTime {
  def localWithOffset(offset: Long): Long = System.currentTimeMillis() + offset

  type Offset = Long
  type Time = Long
}

protected case class NetworkTime(offset: NetworkTime.Offset, lastUpdate: NetworkTime.Time)

case class NetworkTimeProviderSettings(server: String, updateEvery: FiniteDuration, timeout: FiniteDuration)

class NetworkTimeProvider(ntpSettings: NetworkTimeProviderSettings) extends StrictLogging {

  private var state: State = Right(NetworkTime(0L, 0L))
  private var delta: Time = 0L

  private type State = Either[(NetworkTime, Future[NetworkTime]), NetworkTime]

  private def updateOffSet(): Option[NetworkTime.Offset] = {
    val client: NTPUDPClient = new NTPUDPClient()
    client.setDefaultTimeout(ntpSettings.timeout.toMillis.toInt)
    try {
      client.open()
      val info: TimeInfo = client.getTime(InetAddress.getByName(ntpSettings.server))
      info.computeDetails()
      Option(info.getOffset)
    } catch {
      case t: Throwable => None
    } finally {
      client.close()
    }
  }

  private def timeAndState(currentState: State): Future[(NetworkTime.Time, State)] =
    currentState match {
      case Right(nt) =>
        val time: Long = NetworkTime.localWithOffset(nt.offset)
        val state: Either[(NetworkTime, Future[NetworkTime]), NetworkTime] =
          if (time > nt.lastUpdate + ntpSettings.updateEvery.toMillis) {
            Left(nt -> Future(updateOffSet()).map { mbOffset =>
              logger.info("New offset adjusted: " + mbOffset)
              val offset = mbOffset.getOrElse(nt.offset)
              NetworkTime(offset, NetworkTime.localWithOffset(offset))
            })
          } else Right(nt)
        Future.successful((time, state))
      case Left((nt, networkTimeFuture)) =>
        networkTimeFuture
          .map(networkTime => NetworkTime.localWithOffset(networkTime.offset) -> Right(networkTime))
          .recover {
            case NonFatal(th) =>
              logger.warn(s"Failed to evaluate networkTimeFuture $th")
              NetworkTime.localWithOffset(nt.offset) -> Left(nt -> networkTimeFuture)
          }
    }

  def estimatedTime: Time = state match {
    case Right(nt) if NetworkTime.localWithOffset(nt.offset) <= nt.lastUpdate + ntpSettings.updateEvery.toMillis =>
      NetworkTime.localWithOffset(nt.offset)
    case _ => System.currentTimeMillis() + delta
  }

  def time(): Future[NetworkTime.Time] =
    timeAndState(state)
      .map { case (timeFutureResult, stateFutureResult) =>
        state = stateFutureResult
        delta = timeFutureResult - System.currentTimeMillis()
        timeFutureResult
      }
}