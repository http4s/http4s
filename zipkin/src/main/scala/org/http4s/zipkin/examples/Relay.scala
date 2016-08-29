package org.http4s.zipkin.examples

import java.time.Instant

import org.http4s.Uri
import org.http4s.Uri.IPv4
import org.http4s.zipkin.interpreters

import scalaz.concurrent.Task

object Relay extends SimpleRelayServerApp(
  getConfig = getConfig,
  serviceDiscovery = fakeServiceDiscovery,
  interpreters.randomness.default,
  interpreters.clock.default
)
