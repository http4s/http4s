package org.http4s

import org.http4s.dsl.impl.RequestGenerator

package object dsl extends Http4s {
  val GET = Method.GET
  val HEAD = Method.HEAD
  val POST = Method.POST
  val PUT = Method.PUT
  val DELETE = Method.DELETE
  val CONNECT = Method.CONNECT
  val OPTIONS = Method.OPTIONS
  val TRACE = Method.TRACE
  val PATCH = Method.PATCH

  implicit class RequestSyntax(val method: Method) extends AnyVal with RequestGenerator
}
