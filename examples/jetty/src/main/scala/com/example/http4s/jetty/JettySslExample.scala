package com.example.http4s
package jetty

import com.example.http4s.ssl.SslExample
import org.http4s.server.jetty.JettyBuilder

object JettySslExample extends SslExample {
  go(JettyBuilder)
}

