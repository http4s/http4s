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
import fs2.io.file.Files
import fs2.io.file.Path
import fs2.io.process.ProcessBuilder
import org.http4s._
import org.http4s.server.Server
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame

import java.nio.file.Paths
import scala.concurrent.duration._

/** Runs the Autobahn fuzzing client against an ember WebSocket echo server.
  * Uses `wstest` from `WSTEST` or the PATH, otherwise the
  * `crossbario/autobahn-testsuite` docker image. Skipped when neither is
  * available, unless running in CI.
  */
class AutobahnSuite extends Http4sSuite {
  override def munitIOTimeout: Duration = 10.minutes

  private val image = "crossbario/autobahn-testsuite:latest"

  private val knownFailures = Set(
    "3.1",
    "3.2",
    "3.3",
    "3.4",
    "3.5",
    "3.6",
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

  private def onPath(name: String): Option[String] =
    sys.env
      .getOrElse("PATH", "")
      .split(java.io.File.pathSeparator)
      .map(Paths.get(_, name))
      .find(java.nio.file.Files.isExecutable)
      .map(_.toString)

  private val wstest: Option[String] = sys.env.get("WSTEST").orElse(onPath("wstest"))

  private val docker: Option[String] = onPath("docker")

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

  private val testCase =
    """"(\d+(?:\.\d+)+)"\s*:\s*\{\s*"behavior"\s*:\s*"([^"]+)"\s*,\s*"behaviorClose"\s*:\s*"([^"]+)"""".r

  private def testCases(index: String): List[(String, Boolean)] =
    testCase
      .findAllMatchIn(index)
      .map(m => (m.group(1), m.group(2) == "FAILED" || m.group(3) == "FAILED"))
      .toList

  private def spec(port: Int, outdir: String): String =
    s"""{
       |  "outdir": "$outdir",
       |  "servers": [{ "agent": "ember", "url": "ws://127.0.0.1:$port" }],
       |  "cases": ["*"],
       |  "exclude-cases": ["12.*", "13.*"],
       |  "exclude-agent-cases": {}
       |}""".stripMargin

  private def output(cmd: String, args: List[String]): IO[(String, String, Int)] =
    ProcessBuilder(cmd, args).spawn[IO].use { p =>
      (
        p.stdout.through(fs2.text.utf8.decode).compile.string,
        p.stderr.through(fs2.text.utf8.decode).compile.string,
        p.exitValue,
      ).parTupled
    }

  private def command(dir: Path): IO[(String, List[String])] =
    (wstest, docker) match {
      case (Some(bin), _) =>
        IO.pure(bin -> List("-m", "fuzzingclient", "-s", (dir / "spec.json").toString))
      case (None, Some(bin)) =>
        (output("id", List("-u")), output("id", List("-g"))).mapN { (uid, gid) =>
          bin -> List(
            "run",
            "--rm",
            "--network",
            "host",
            "--platform",
            "linux/amd64",
            "--user",
            s"${uid._1.trim}:${gid._1.trim}",
            "-v",
            s"$dir:/autobahn",
            image,
            "wstest",
            "-m",
            "fuzzingclient",
            "-s",
            "/autobahn/spec.json",
          )
        }
      case (None, None) => IO(fail("neither wstest nor docker found on PATH"))
    }

  test("autobahn fuzzingclient") {
    assume(
      wstest.isDefined || docker.isDefined || sys.env.contains("CI"),
      "wstest and docker not found",
    )
    (server, Files[IO].tempDirectory).tupled.use { case (server, dir) =>
      val outdir = if (wstest.isDefined) (dir / "reports").toString else "/autobahn/reports"
      for {
        _ <- fs2.Stream
          .emit(spec(server.addressIp4s.port.value, outdir))
          .through(Files[IO].writeUtf8(dir / "spec.json"))
          .compile
          .drain
        cmd <- command(dir)
        result <- output(cmd._1, cmd._2)
        (stdout, stderr, exit) = result
        log = s"exit code $exit\nstdout:\n$stdout\nstderr:\n$stderr"
        index <- Files[IO]
          .readUtf8(dir / "reports" / "index.json")
          .compile
          .string
          .adaptError { case e => new AssertionError(s"no Autobahn report:\n$log", e) }
      } yield {
        val cases = testCases(index)
        val failures = cases.collect { case (id, true) => id }.toSet
        val unexpected = (failures -- knownFailures).toList.sorted
        val fixed = (knownFailures -- failures).toList.sorted
        assert(cases.nonEmpty, s"no Autobahn test cases parsed from the report:\n$index\n\n$log")
        assert(
          unexpected.isEmpty,
          unexpected.mkString("Unexpected Autobahn failures:\n", "\n", s"\n\n$log"),
        )
        assert(
          fixed.isEmpty,
          fixed.mkString("Known Autobahn failures now pass, remove them:\n", "\n", ""),
        )
      }
    }
  }
}
