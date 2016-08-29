package org.http4s.zipkin.examples

import java.time.Instant
import java.util.Random

import org.http4s.HttpService
import org.http4s.client.blaze.PooledHttp1Client
import org.http4s.dsl._
import org.http4s.server._
import org.http4s.server.blaze._
import org.http4s.zipkin.algebras.{Clock, Randomness}
import org.http4s.zipkin.interpreters
import org.http4s.zipkin.interpreters.collector.Http
import org.http4s.zipkin.middleware.{ZipkinServer, ZipkinService}
import org.http4s.zipkin.models.{Endpoint, ServerIds}

import scalaz.concurrent.Task
import scalaz.{Kleisli, Scalaz}

object EndOfTheLine extends SimpleResponseServerApp(
  getEndpointFromConsole,
  interpreters.randomness.default,
  interpreters.clock.default
)

