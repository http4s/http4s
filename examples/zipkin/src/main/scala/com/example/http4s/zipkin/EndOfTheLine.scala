package com.example.http4s.zipkin

import org.http4s.zipkin.interpreters

object EndOfTheLine extends SimpleResponseServerApp(
  getEndpointFromConsole,
  interpreters.randomness.default,
  interpreters.clock.default
)

