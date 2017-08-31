package com.example.http4s.zipkin

import org.http4s.zipkin.core.interpreters

object Relay extends SimpleRelayServerApp(
  getConfig = getConfig,
  serviceDiscovery = fakeServiceDiscovery,
  interpreters.randomness.default,
  interpreters.clock.default
)
