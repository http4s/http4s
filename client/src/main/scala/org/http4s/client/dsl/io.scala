/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package dsl

import cats.effect.IO

/** Provides extension methods for using a http4s [[org.http4s.client.Client]]
  * {{{
  *   import cats.effect.IO
  *   import org.http4s._
  *   import org.http4s.client._
  *   import org.http4s.client.io._
  *   import org.http4s.Http4s._
  *   import org.http4s.Status._
  *   import org.http4s.Method._
  *   import org.http4s.EntityDecoder
  *
  *   def client: Client[IO] = ???
  *
  *   val r: IO[String] = client(GET(uri("https://www.foo.bar/"))).as[String]
  *   val r2: DecodeResult[String] = client(GET(uri("https://www.foo.bar/"))).attemptAs[String] // implicitly resolve the decoder
  *   val req1 = r.unsafeRunSync()
  *   val req2 = r.unsafeRunSync() // Each invocation fetches a new Result based on the behavior of the Client
  *
  * }}}
  */
object io extends Http4sClientDsl[IO]
