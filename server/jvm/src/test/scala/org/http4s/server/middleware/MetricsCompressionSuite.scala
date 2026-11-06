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

package org.http4s.server.middleware

import cats.effect.IO
import fs2.io.compression._
import org.http4s._
import org.http4s.headers.`Accept-Encoding`
import org.http4s.headers.`Content-Encoding`
import org.http4s.metrics.TestMetricsOps2

final class MetricsCompressionSuite extends Http4sSuite {
  test("MetricsOps2 observes encoded response bytes when placed outside compression") {
    val content = "a" * 1024
    val app = HttpApp.pure[IO](Response[IO](Status.Ok).withEntity(content))
    val request = Request[IO]().putHeaders(`Accept-Encoding`(ContentCoding.gzip))

    for {
      encodedOps <- TestMetricsOps2.create
      response <- Metrics[IO](encodedOps)(GZip(app)).run(request)
      encoded <- response.body.compile.toVector
      encodedState <- encodedOps.state
      decodedOps <- TestMetricsOps2.create
      decodedResponse <- GZip(Metrics[IO](decodedOps)(app)).run(request)
      _ <- decodedResponse.body.compile.drain
      decodedState <- decodedOps.state
    } yield {
      assertEquals(response.contentLength, None)
      assertEquals(
        response.headers.get[`Content-Encoding`],
        Some(`Content-Encoding`(ContentCoding.gzip)),
      )
      assert(encoded.size < content.length)
      assertEquals(encodedState.responseBodies.map(_.bodySizeBytes), List(encoded.size.toLong))
      assertEquals(decodedState.responseBodies.map(_.bodySizeBytes), List(content.length.toLong))
    }
  }
}
