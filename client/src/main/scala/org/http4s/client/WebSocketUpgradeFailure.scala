package org.http4s
package client

import scala.util.control.NoStackTrace

/** Represents the response from a failed attempt to upgrade to a web socket. */
case class WebSocketUpgradeFailure(resp: Response)
    extends RuntimeException
    with NoStackTrace
