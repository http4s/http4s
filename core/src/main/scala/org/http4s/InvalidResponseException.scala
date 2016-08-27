package org.http4s

import scala.util.control.NoStackTrace

/** Exception dealing with invalid response
  * @param msg description if what makes the response invalid
  */
final case class InvalidResponseException(msg: String) extends Exception(msg) with NoStackTrace
