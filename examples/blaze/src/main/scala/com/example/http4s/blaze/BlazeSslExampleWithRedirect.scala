package com.example.http4s
package blaze

import com.example.http4s.ssl.SslExampleWithRedirect
import org.http4s.server.blaze.BlazeBuilder

object BlazeSslExampleWithRedirect extends SslExampleWithRedirect {
  def builder = BlazeBuilder
}
