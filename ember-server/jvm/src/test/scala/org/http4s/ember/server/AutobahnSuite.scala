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
import fs2.Pipe
import org.http4s._
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.testcontainers.Testcontainers
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName

import java.io.InputStream
import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.io.Source

/** Runs the Autobahn fuzzing client against an ember WebSocket echo server,
  * using the `crossbario/autobahn-testsuite` image through testcontainers.
  * Opt-in: skipped unless `AUTOBAHN` is set.
  */
class AutobahnSuite extends Http4sSuite {
  private val amd64 = Set("amd64", "x86_64").contains(sys.props("os.arch"))

  private val runLimit = if (amd64) 10.minutes else 20.minutes

  override def munitIOTimeout: Duration = 15.minutes + runLimit * 2

  private val image =
    "crossbario/autobahn-testsuite@sha256:519915fb568b04c9383f70a1c405ae3ff44ab9e35835b085239c258b6fac3074"

  private val expectedCases = 301

  private val passing = Set("OK", "NON-STRICT", "INFORMATIONAL", "UNIMPLEMENTED")

  private val knownFailures = Set(
    "3.1",
    "3.2",
    "3.3",
    "3.4",
    "3.5",
    "3.6",
    "3.7",
    "5.9",
    "5.10",
    "5.11",
    "5.12",
    "5.13",
    "5.14",
    "5.15",
    "5.16",
    "5.17",
    "5.18",
    "6.3.1",
    "6.3.2",
    "6.4.1",
    "6.4.2",
    "6.4.3",
    "6.4.4",
    "6.6.1",
    "6.6.3",
    "6.6.4",
    "6.6.6",
    "6.6.8",
    "6.6.10",
    "6.8.1",
    "6.8.2",
    "6.10.1",
    "6.10.2",
    "6.10.3",
    "6.11.5",
    "6.12.1",
    "6.12.2",
    "6.12.3",
    "6.12.4",
    "6.12.5",
    "6.12.6",
    "6.12.7",
    "6.12.8",
    "6.13.1",
    "6.13.2",
    "6.13.3",
    "6.13.4",
    "6.13.5",
    "6.14.1",
    "6.14.2",
    "6.14.3",
    "6.14.4",
    "6.14.5",
    "6.14.6",
    "6.14.7",
    "6.14.8",
    "6.14.9",
    "6.14.10",
    "6.15.1",
    "6.16.1",
    "6.16.2",
    "6.16.3",
    "6.17.1",
    "6.17.2",
    "6.17.3",
    "6.17.4",
    "6.17.5",
    "6.18.1",
    "6.18.2",
    "6.18.3",
    "6.18.4",
    "6.18.5",
    "6.19.1",
    "6.19.2",
    "6.19.3",
    "6.19.4",
    "6.19.5",
    "6.20.1",
    "6.20.2",
    "6.20.3",
    "6.20.4",
    "6.20.5",
    "6.20.6",
    "6.20.7",
    "6.21.1",
    "6.21.2",
    "6.21.3",
    "6.21.4",
    "6.21.5",
    "6.21.6",
    "6.21.7",
    "6.21.8",
    "7.3.6",
    "7.5.1",
    "7.9.1",
    "7.9.2",
    "7.9.3",
    "7.9.4",
    "7.9.5",
    "7.9.6",
    "7.9.7",
    "7.9.8",
    "7.9.9",
  )

  private final class AutobahnContainer
      extends GenericContainer[AutobahnContainer](DockerImageName.parse(image))

  private def container(port: Int): Resource[IO, AutobahnContainer] =
    Resource.make(IO.blocking {
      Testcontainers.exposeHostPorts(port)
      val c = new AutobahnContainer().withCommand("sleep", "infinity")
      c.start()
      c
    })(c => IO.blocking(c.stop()))

  private val echo: Pipe[IO, WebSocketFrame, WebSocketFrame] = _.collect {
    case f: WebSocketFrame.Text => f
    case f: WebSocketFrame.Binary => f
  }

  private val server: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"0")
      .withHttpWebSocketApp { (wsb: WebSocketBuilder2[IO]) =>
        HttpApp[IO](_ =>
          wsb
            .withDefragment(true)
            .withMaxFragmentCount(Int.MaxValue)
            .build(echo)
        )
      }
      .build

  private val entry = """"(\d+(?:\.\d+)+)"\s*:\s*\{([^{}]*)\}""".r

  private def field(body: String, name: String): Option[String] =
    s""""$name"\\s*:\\s*"([^"]*)"""".r.findFirstMatchIn(body).map(_.group(1))

  private def verdicts(index: String): Map[String, Boolean] =
    entry
      .findAllMatchIn(index)
      .map { m =>
        val body = m.group(2)
        m.group(1) -> !List("behavior", "behaviorClose").forall(field(body, _).exists(passing))
      }
      .toMap

  private def spec(port: Int, run: String, cases: List[String]): String =
    s"""{
       |  "outdir": "/autobahn/$run",
       |  "servers": [{ "agent": "ember", "url": "ws://host.testcontainers.internal:$port" }],
       |  "cases": [${cases.map(c => s""""$c"""").mkString(", ")}],
       |  "exclude-cases": ["12.*", "13.*"],
       |  "exclude-agent-cases": {}
       |}""".stripMargin

  private def read(c: AutobahnContainer, path: String): IO[Option[String]] =
    IO.blocking(
      c.copyFileFromContainer[String](
        path,
        (in: InputStream) => Source.fromInputStream(in, "UTF-8").mkString,
      )
    ).map(_.some)
      .handleError(_ => None)

  private def fuzz(
      c: AutobahnContainer,
      port: Int,
      run: String,
      cases: List[String],
      expected: Int,
  ): IO[(Map[String, Boolean], String)] = {
    val log = read(c, s"/autobahn/$run.log").map(_.getOrElse(""))
    for {
      _ <- IO.blocking(
        c.copyFileToContainer(
          Transferable.of(spec(port, run, cases).getBytes(StandardCharsets.UTF_8)),
          s"/autobahn/$run.json",
        )
      )
      exit <- IO
        .interruptible(
          c.execInContainer(
            "sh",
            "-c",
            s"wstest -m fuzzingclient -s /autobahn/$run.json > /autobahn/$run.log 2>&1",
          ).getExitCode
            .intValue
        )
        .timeoutTo(
          runLimit,
          log.flatMap(l =>
            IO.raiseError(new AssertionError(s"wstest timed out after $runLimit\n\n$l"))
          ),
        )
      output <- log.map(l => s"$run: exit code $exit\n$l")
      _ <- IO(assert(exit == 0, s"wstest failed\n\n$output"))
      index <- read(c, s"/autobahn/$run/index.json")
        .flatMap(IO.fromOption(_)(new AssertionError(s"no Autobahn report\n\n$output")))
      parsed = verdicts(index)
      _ <- IO(
        assertEquals(
          parsed.size,
          expected,
          s"unexpected number of Autobahn cases:\n$index\n\n$output",
        )
      )
    } yield (parsed, output)
  }

  test("autobahn fuzzingclient") {
    assume(sys.env.contains("AUTOBAHN"), "set AUTOBAHN=1 to run the Autobahn testsuite")
    server.mproduct(s => container(s.addressIp4s.port.value)).use { case (server, c) =>
      val port = server.addressIp4s.port.value
      for {
        first <- fuzz(c, port, "all", List("*"), expectedCases)
        (results, log) = first
        missing = (knownFailures -- results.keySet).toList.sorted
        _ <- IO(
          assert(
            missing.isEmpty,
            missing.mkString("Known Autobahn failures missing from the report:\n", "\n", ""),
          )
        )
        suspicious = results
          .collect {
            case (id, failed) if failed != knownFailures(id) => id
          }
          .toList
          .sorted
        second <-
          if (suspicious.isEmpty) IO.pure((Map.empty[String, Boolean], ""))
          else fuzz(c, port, "retry", suspicious, suspicious.size)
        (retried, retryLog) = second
      } yield {
        val confirmed = suspicious.filter(id => retried(id) != knownFailures(id))
        val unexpected = confirmed.filterNot(knownFailures)
        val fixed = confirmed.filter(knownFailures)
        val logs = s"$log\n$retryLog"
        assert(
          unexpected.isEmpty,
          unexpected.mkString("Unexpected Autobahn failures:\n", "\n", s"\n\n$logs"),
        )
        assert(
          fixed.isEmpty,
          fixed.mkString("Known Autobahn failures now pass, remove them:\n", "\n", s"\n\n$logs"),
        )
      }
    }
  }
}
