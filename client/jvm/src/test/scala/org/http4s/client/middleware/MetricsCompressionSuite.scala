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

package org.http4s.client.middleware

import cats.effect.IO
import cats.effect.Resource
import fs2.Stream
import fs2.io.compression._
import org.http4s._
import org.http4s.client.Client
import org.http4s.headers.`Accept-Encoding`
import org.http4s.metrics.TestMetricsOps2
import org.http4s.server.middleware.{GZip => ServerGZip}
import org.http4s.syntax.all._

final class MetricsCompressionSuite extends Http4sSuite {
  test("MetricsOps2 observes encoded response bytes when placed inside decompression") {
    val content = "a" * 1024
    val compressedApp = ServerGZip(
      HttpApp.pure[IO](Response[IO](Status.Ok).withEntity(content))
    )
    val req = Request[IO](uri = uri"/x")

    for {
      encodedResponse <- compressedApp.run(
        req.putHeaders(`Accept-Encoding`(ContentCoding.gzip))
      )
      encoded <- encodedResponse.body.compile.toVector
      transport = Client[IO](_ =>
        Resource.pure(
          encodedResponse.withBodyStream(Stream.emits(encoded).covary[IO])
        )
      )
      encodedOps <- TestMetricsOps2.create
      decoded <- GZip()(Metrics[IO](encodedOps)(transport)).expect[String](req)
      encodedState <- encodedOps.state
      decodedOps <- TestMetricsOps2.create
      _ <- Metrics[IO](decodedOps)(GZip()(transport)).expect[String](req)
      decodedState <- decodedOps.state
    } yield {
      assertEquals(decoded, content)
      assert(encoded.size < content.length)
      assertEquals(encodedState.responseBodies.map(_.bodySizeBytes), List(encoded.size.toLong))
      assertEquals(decodedState.responseBodies.map(_.bodySizeBytes), List(content.length.toLong))
    }
  }
}
