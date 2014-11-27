package org.http4s.client

import org.http4s.{Response, Headers, Status}

import scala.util.control.NoStackTrace

sealed trait ResultType[+T] {
  def status: Status
  def headers: Headers
}

case class Successful[T](status: Status, headers: Headers, body: T) extends ResultType[T]

case class UnHandled(resp: Response) extends Exception with ResultType[Nothing] with NoStackTrace {
  override def status: Status = resp.status
  override def headers: Headers = resp.headers
}

case class BadResponse(resp: Response, msg: String) extends Exception with ResultType[Nothing] with NoStackTrace {
  override def getMessage: String = s"Bad Response, $status: '$msg'"
  override def status: Status = resp.status
  override def headers: Headers = resp.headers
}