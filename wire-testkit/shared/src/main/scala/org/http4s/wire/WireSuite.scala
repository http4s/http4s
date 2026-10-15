/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all._
import munit.Location
import munit.TestOptions

import java.util.concurrent.TimeoutException
import scala.concurrent.duration._

abstract class WireSuite(
    target: WireTarget[IO],
    protocol: Protocol,
    role: Role,
    comparison: Boolean,
) extends RuntimeDiagnostics.Suite {
  override final def munitFlakyOK: Boolean = false
  override final def munitIOTimeout: Duration = 15.seconds

  private val cases = Catalog.select(protocol, role)
  private val integrityProblems = IgnoreRegistry.validate(Catalog.all) ++ SpecInventory.problems
  private val report = new WireReport(target, getClass.getSimpleName, comparison)
  private val reporting = ResourceSuiteLocalFixture(
    "wire report",
    Resource.make(
      IO.raiseWhen(integrityProblems.nonEmpty)(
        new IllegalStateException(integrityProblems.mkString("\n"))
      ) *>
        report.initialize(cases) *> cases.traverse_ { c =>
          val ignored = if (comparison) None else IgnoreRegistry.get(c.id, RuntimeInfo.platform)
          ignored.traverse_(entry => report.record(c, "skipped", 0L, entry.observed, ""))
        }
    )(_ => report.complete),
  )
  override def munitFixtures: Seq[munit.AnyFixture[_]] = List(reporting)

  cases.foreach { c =>
    implicit val location: Location = c.location
    val ignored = !comparison && IgnoreRegistry.get(c.id, RuntimeInfo.platform).isDefined
    val options = TestOptions(c.id).tag(new munit.Tag(c.id))
    test(if (ignored) options.ignore else options) {
      for {
        transcript <- Transcript.create[IO]()
        started <- IO.monotonic
        outcome <- WireSuite.runCase(target, c, transcript, 8.seconds).attempt
        ended <- IO.monotonic
        trace <- transcript.render
        status = WireSuite.outcomeStatus(outcome)
        _ <- report.record(
          c,
          status,
          (ended - started).toMillis,
          outcome.fold(e => s"${e.getClass.getName}: ${e.getMessage}", _ => ""),
          trace,
        )
        _ <- outcome match {
          case Right(_) => IO.unit
          case Left(error) =>
            // Native's test-runner transport must carry this failure event too.
            // Keep console evidence small; the report retains the full bounded trace.
            val inlineTrace = trace.take(4096)
            val evidence = s"Full transcript: ${report.transcriptPath(c.id)}"
            IO(
              fail(
                s"${c.id}: $status\n${c.references.map(_.url).mkString("\n")}\n${error.toString}\n$inlineTrace\n$evidence",
                error,
              )
            )
        }
      } yield ()
    }
  }
}

private[wire] object WireSuite {
  def runCase(
      target: WireTarget[IO],
      c: WireCase,
      transcript: Transcript[IO],
      deadline: FiniteDuration,
  ): IO[Unit] =
    AdapterFailure.observe(target)(monitored =>
      c.run(CaseContext(monitored, transcript)).timeout(deadline)
    )

  def outcomeStatus(outcome: Either[Throwable, Unit]): String = outcome match {
    case Right(_) => "pass"
    case Left(error) if AdapterFailure.find(error).nonEmpty => "error"
    case Left(_: WireObservationLimit) => "error"
    case Left(_: ProtocolFailure) => "fail"
    case Left(_: WireDecodingFailure) => "fail"
    case Left(_: TimeoutException) => "inconclusive"
    case Left(_) => "error"
  }
}
