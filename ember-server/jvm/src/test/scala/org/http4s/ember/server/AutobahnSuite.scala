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
import java.util.UUID
import scala.concurrent.duration._

/** Runs the Autobahn fuzzing client against an ember WebSocket echo server.
  * Opt-in: skipped unless `AUTOBAHN` is set. Uses `wstest` from `WSTEST` or
  * the PATH, otherwise the `crossbario/autobahn-testsuite` docker image, which
  * needs a local rootful Linux daemon and amd64 or emulation.
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

  private def onPath(name: String): Option[String] =
    sys.env
      .getOrElse("PATH", "")
      .split(java.io.File.pathSeparator)
      .map(Paths.get(_, name))
      .find(java.nio.file.Files.isExecutable)
      .map(_.toString)

  private def output(
      cmd: String,
      args: List[String],
      limit: FiniteDuration,
      onFailure: IO[Unit] = IO.unit,
  ): IO[(String, String, Int)] =
    (Ref[IO].of(Vector.empty[String]), Ref[IO].of(Vector.empty[String])).flatMapN { (out, err) =>
      def collect(s: fs2.Stream[IO, Byte], to: Ref[IO, Vector[String]]) =
        s.through(fs2.text.utf8.decode).foreach(chunk => to.update(_ :+ chunk)).compile.drain
      val logs = (out.get, err.get).mapN((o, e) => (o.mkString, e.mkString))
      ProcessBuilder(cmd, args)
        .spawn[IO]
        .flatTap { _ =>
          Resource.onFinalizeCase[IO] {
            case Resource.ExitCase.Succeeded => IO.unit
            case _ => onFailure
          }
        }
        .use { p =>
          (collect(p.stdout, out), collect(p.stderr, err)).parTupled *> p.exitValue
        }
        .timeoutTo(
          limit,
          logs.flatMap { case (o, e) =>
            IO.raiseError(
              new AssertionError(s"$cmd timed out after $limit\nstdout:\n$o\nstderr:\n$e")
            )
          },
        )
        .flatMap(exit => logs.map { case (o, e) => (o, e, exit) })
    }

  private sealed trait Runner {
    def base(dir: Path): String
    def fuzz(dir: Path, specFile: String): IO[(String, String, Int)]
  }

  private final class Wstest(bin: String) extends Runner {
    def base(dir: Path): String = dir.toString
    def fuzz(dir: Path, specFile: String): IO[(String, String, Int)] =
      output(bin, List("-m", "fuzzingclient", "-s", specFile), runLimit)
  }

  private final class Docker(bin: String) extends Runner {
    def base(dir: Path): String = "/autobahn"
    def fuzz(dir: Path, specFile: String): IO[(String, String, Int)] = {
      val name = s"http4s-autobahn-${UUID.randomUUID()}"
      IO.blocking {
        val nio = dir.toNioPath
        (
          java.nio.file.Files.getAttribute(nio, "unix:uid"),
          java.nio.file.Files.getAttribute(nio, "unix:gid"),
        )
      }.flatMap { case (uid, gid) =>
        output(
          bin,
          List(
            "run",
            "--rm",
            "--init",
            "--name",
            name,
            "--network",
            "host",
            "--platform",
            "linux/amd64",
            "--user",
            s"$uid:$gid",
            "-v",
            s"$dir:/autobahn:z",
            image,
            "wstest",
            "-m",
            "fuzzingclient",
            "-s",
            specFile,
          ),
          runLimit,
          output(bin, List("rm", "-f", name), 1.minute).void,
        )
      }
    }
  }

  private def dockerRunner(bin: String): IO[Either[String, Runner]] = {
    def succeeds(limit: FiniteDuration, args: String*): IO[Option[String]] =
      output(bin, args.toList, limit)
        .map { case (out, _, exit) => if (exit == 0) Some(out) else None }
        .handleError(_ => None)
    val localDaemon = sys.env.get("DOCKER_HOST").forall(_.startsWith("unix://"))
    val checks: List[(IO[Boolean], String)] = List(
      IO.pure(sys.props("os.name") == "Linux") -> "docker mode needs Linux for --network host",
      IO.pure(localDaemon) -> "docker mode needs a local daemon, DOCKER_HOST points elsewhere",
      succeeds(1.minute, "info", "--format", "{{.OperatingSystem}} {{json .SecurityOptions}}")
        .map(_.exists(info => !info.contains("rootless") && !info.contains("Docker Desktop"))) ->
        "docker mode needs a rootful daemon on this host, not rootless or Docker Desktop",
      succeeds(10.minutes, "pull", "-q", "--platform", "linux/amd64", image)
        .map(_.isDefined) -> s"could not pull $image",
      (if (amd64) IO.pure(true)
       else
         succeeds(5.minutes, "run", "--rm", "--platform", "linux/amd64", image, "true")
           .map(_.isDefined)) -> "docker cannot run linux/amd64 images, set up emulation",
    )
    checks.foldRight(IO.pure[Either[String, Runner]](Right(new Docker(bin)))) {
      case ((check, reason), next) => check.ifM(next, IO.pure(Left(reason)))
    }
  }

  private val runner: IO[Either[String, Runner]] =
    sys.env.get("WSTEST").orElse(onPath("wstest")) match {
      case Some(bin) => IO.pure(Right(new Wstest(bin)))
      case None =>
        onPath("docker") match {
          case Some(bin) => dockerRunner(bin)
          case None => IO.pure(Left("neither wstest nor docker found on PATH"))
        }
    }

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

  private def json(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def spec(port: Int, outdir: String, cases: List[String]): String =
    s"""{
       |  "outdir": ${json(outdir)},
       |  "servers": [{ "agent": "ember", "url": "ws://127.0.0.1:$port" }],
       |  "cases": [${cases.map(json).mkString(", ")}],
       |  "exclude-cases": ["12.*", "13.*"],
       |  "exclude-agent-cases": {}
       |}""".stripMargin

  private def fuzz(
      runner: Runner,
      dir: Path,
      port: Int,
      run: String,
      cases: List[String],
      expected: Int,
  ): IO[(Map[String, Boolean], String)] = {
    val base = runner.base(dir)
    for {
      _ <- fs2.Stream
        .emit(spec(port, s"$base/$run", cases))
        .through(Files[IO].writeUtf8(dir / s"$run.json"))
        .compile
        .drain
      result <- runner.fuzz(dir, s"$base/$run.json")
      (stdout, stderr, exit) = result
      log = s"$run: exit code $exit\nstdout:\n$stdout\nstderr:\n$stderr"
      _ <- IO(assert(exit == 0, s"wstest failed\n\n$log"))
      index <- Files[IO]
        .readUtf8(dir / run / "index.json")
        .compile
        .string
        .adaptError { case e => new AssertionError(s"no Autobahn report\n\n$log", e) }
      parsed = verdicts(index)
      _ <- IO(
        assertEquals(parsed.size, expected, s"unexpected number of Autobahn cases:\n$index\n\n$log")
      )
    } yield (parsed, log)
  }

  test("autobahn fuzzingclient") {
    assume(sys.env.contains("AUTOBAHN"), "set AUTOBAHN=1 to run the Autobahn testsuite")
    runner.flatMap {
      case Left(reason) => IO(fail(reason))
      case Right(runner) =>
        (server, Files[IO].tempDirectory).tupled.use { case (server, dir) =>
          val port = server.addressIp4s.port.value
          for {
            first <- fuzz(runner, dir, port, "all", List("*"), expectedCases)
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
              else fuzz(runner, dir, port, "retry", suspicious, suspicious.size)
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
}
