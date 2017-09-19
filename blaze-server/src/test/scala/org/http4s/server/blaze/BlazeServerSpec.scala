package org.http4s
package server
package blaze

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

class BlazeServerSpec extends ServerSpec {
  def builder = BlazeBuilder
}
