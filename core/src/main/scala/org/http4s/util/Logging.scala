package org.http4s.util

import com.typesafe.scalalogging.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A temporary workaround for https://github.com/typesafehub/scala-logging/issues/13
 */
private[http4s] trait Logging { // private, because I hope it goes away
  protected lazy val logger: Logger = Logger(LoggerFactory getLogger getClass.getName)
}
