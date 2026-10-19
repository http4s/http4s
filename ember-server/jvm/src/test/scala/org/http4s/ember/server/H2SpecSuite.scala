/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.io.file.Files
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext
import fs2.io.process.ProcessBuilder
import org.http4s._
import org.http4s.server.Server

import java.nio.file.Paths
import scala.concurrent.duration._

/** Runs the h2spec conformance suite against an ember HTTP/2 server, over
  * cleartext (prior knowledge) and over TLS. The binary comes from `H2SPEC` or
  * the PATH. Skipped when it is missing, unless running in CI.
  */
class H2SpecSuite extends Http4sSuite {
  override def munitIOTimeout: Duration = 3.minutes

  private val knownTlsFailures = Set(
    "http2/5.1: closed: Sends a DATA frame",
    "http2/5.1: closed: Sends a HEADERS frame",
    "http2/8.1: Sends a second HEADERS frame without the END_STREAM flag",
    "http2/8.1.2.1: Sends a HEADERS frame that contains a pseudo-header field as trailers",
    "hpack/4.2: Sends a dynamic table size update at the end of header block",
    "hpack/5.2: Sends a Huffman-encoded string literal representation with padding longer than 7 bits",
  )

  private val knownCleartextFailures =
    knownTlsFailures + "http2/3.5: Sends invalid connection preface"

  private val binary: Option[String] =
    sys.env
      .get("H2SPEC")
      .orElse(
        sys.env
          .getOrElse("PATH", "")
          .split(java.io.File.pathSeparator)
          .map(Paths.get(_, "h2spec"))
          .find(java.nio.file.Files.isExecutable)
          .map(_.toString)
      )

  private val app: HttpApp[IO] =
    HttpApp(req => req.body.compile.drain.as(Response[IO](Status.Ok).withEntity("ok")))

  private val testCase =
    """<testcase package="([^"]+)" classname="([^"]+)"[^>]*>(\s*<(?:error|failure))?""".r

  private def unescape(s: String): String =
    s.replace("&#34;", "\"")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&amp;", "&")

  private def testCases(report: String): List[(String, Boolean)] =
    testCase
      .findAllMatchIn(report)
      .map(m => (s"${m.group(1)}: ${unescape(m.group(2))}", m.group(3) != null))
      .toList

  private def server(tls: Option[TLSContext[IO]]): Resource[IO, Server] = {
    val builder = EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"0")
      .withHttp2
      .withHttpApp(app)
    tls.fold(builder)(builder.withTLS(_)).build
  }

  private def h2spec(tls: Boolean, knownFailures: Set[String]): IO[Unit] = {
    assume(binary.isDefined || sys.env.contains("CI"), "h2spec binary not found")
    val h2spec = binary.getOrElse(fail("h2spec binary not found on PATH"))
    val tlsContext =
      if (tls)
        Resource
          .eval(
            Network[IO].tlsContext
              .fromKeyStoreResource("keystore.jks", "password".toCharArray, "password".toCharArray)
          )
          .map(_.some)
      else Resource.pure[IO, Option[TLSContext[IO]]](None)
    val resources = for {
      ctx <- tlsContext
      server <- server(ctx)
      report <- Files[IO].tempFile(None, "h2spec", ".xml", None)
    } yield (server, report)
    resources.use { case (server, report) =>
      val args = List(
        "-h",
        "127.0.0.1",
        "-p",
        server.addressIp4s.port.value.toString,
        "-j",
        report.toString,
      ) ++ (if (tls) List("-t", "-k") else Nil)
      val run = ProcessBuilder(h2spec, args).spawn[IO].use { p =>
        (
          p.stdout.through(fs2.text.utf8.decode).compile.string,
          p.stderr.through(fs2.text.utf8.decode).compile.string,
          p.exitValue,
        ).parTupled
      }
      for {
        result <- run
        (stdout, stderr, exit) = result
        xml <- Files[IO].readUtf8(report).compile.string
      } yield {
        val output = s"exit code $exit\nstdout:\n$stdout\nstderr:\n$stderr"
        val cases = testCases(xml)
        val failures = cases.collect { case (name, true) => name }.toSet
        val unexpected = (failures -- knownFailures).toList.sorted
        val fixed = (knownFailures -- failures).toList.sorted
        assert(cases.nonEmpty, s"no h2spec test cases parsed from the report:\n$xml\n\n$output")
        assert(
          unexpected.isEmpty,
          unexpected.mkString("Unexpected h2spec failures:\n", "\n", s"\n\n$output"),
        )
        assert(
          fixed.isEmpty,
          fixed.mkString("Known h2spec failures now pass, remove them:\n", "\n", ""),
        )
      }
    }
  }

  test("h2spec over cleartext")(h2spec(tls = false, knownCleartextFailures))

  test("h2spec over TLS")(h2spec(tls = true, knownTlsFailures))
}
