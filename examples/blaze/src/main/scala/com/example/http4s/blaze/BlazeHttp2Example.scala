package com.example.http4s
package blaze

import cats.effect._
import com.example.http4s.ssl.SslExample
import org.http4s.server.blaze.BlazeBuilder

object BlazeHttp2Example extends SslExample {
  def builder = BlazeBuilder[IO].enableHttp2(true)
}
