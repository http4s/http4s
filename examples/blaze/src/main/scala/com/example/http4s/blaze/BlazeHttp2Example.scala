package com.example.http4s
package blaze

import cats.effect._
import com.example.http4s.ssl.SslExample
import org.http4s.server.blaze.BlazeBuilder
import scala.concurrent.ExecutionContext.Implicits.global

object BlazeHttp2Example extends SslExample[IO] {
  def builder: BlazeBuilder[IO] = BlazeBuilder[IO].enableHttp2(true)
}
