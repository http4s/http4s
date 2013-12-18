package org.http4s.netty

import org.eclipse.jetty.npn.NextProtoNego
import com.typesafe.scalalogging.slf4j.Logging
import java.util.List

/**
 * @author Bryce Anderson
 *         Created on 12/17/13
 */


class NPNProvider extends NextProtoNego.ServerProvider with Logging {
  private var selected = ""
  def protocolSelected(protocol: String) {
    logger.trace("NPN selected protocol: " + protocol)
    selected = protocol
  }

  def get = selected

  def unsupported() {
    logger.trace("NPN not supported")
  }

  def protocols(): List[String] = {
    import collection.JavaConversions._
    "spdy/3.1"::"http/1.1"::Nil
  }
}