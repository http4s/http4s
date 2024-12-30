/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package client
package dsl

import cats.effect.IO

/** Provides extension methods for using a http4s [[org.http4s.client.Client]]
  * {{{
  *
  *   import cats.effect.IO
  *   import cats.effect.unsafe.implicits.global
  *   import org.http4s.client._
  *   import org.http4s.client.dsl.io._
  *   import org.http4s.Method._
  *   import org.http4s.syntax.all._
  *
  *   def client: Client[IO] = ???
  *
  *   val r: IO[String] = client.run(GET(uri"https://www.foo.bar/")).use(_.as[String])
  *   val r2: IO[String] = client.fetchAs[String](GET(uri"https://www.foo.bar/")) // implicitly resolve the decoder
  *
  *   val req1 = r.unsafeRunSync() // note that you shouldn't use `unsafeRunSync` in your application!
  *   val req2 = r2.unsafeRunSync() // Each invocation fetches a new Result based on the behavior of the Client
  *
  * }}}
  */
object io extends Http4sClientDsl[IO]
