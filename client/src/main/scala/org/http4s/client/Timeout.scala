package org.http4s
package client

import cats._
import scala.concurrent.duration._

/** ADT for the timeout types which may be used by a http4s client.
  *
  * Not all clients support all timeout types.
  *
  * See [[Timeouts]] for a mechanism to construct timeout values which always
  * line up to expected constraints, e.g. responseHeaderTimeout < idleTimeout.
  */
sealed trait Timeout extends Product with Serializable {
  def value: Duration
}

object Timeout {

  implicit val showInstance: Show[Timeout] =
    Show.fromToString

  final case class ConnectTimeout(override final val value: Duration) extends Timeout

  object ConnectTimeout {
    val Inf: ConnectTimeout = ConnectTimeout(Duration.Inf)
    val default: ConnectTimeout = ConnectTimeout(10.seconds)
  }

  final case class IdleTimeout(override final val value: Duration) extends Timeout

  object IdleTimeout {
    val Inf: IdleTimeout = IdleTimeout(Duration.Inf)
    val default: IdleTimeout = IdleTimeout(1.minute)
  }

  final case class RequestTimeout(override final val value: Duration) extends Timeout

  object RequestTimeout {
    val Inf: RequestTimeout = RequestTimeout(Duration.Inf)
    val default: RequestTimeout = RequestTimeout(45.seconds)
  }

  final case class ResponseHeaderTimeout(override final val value: Duration) extends Timeout

  object ResponseHeaderTimeout {
    val Inf: ResponseHeaderTimeout = ResponseHeaderTimeout(Duration.Inf)
    val default: ResponseHeaderTimeout = this.Inf
  }
}
