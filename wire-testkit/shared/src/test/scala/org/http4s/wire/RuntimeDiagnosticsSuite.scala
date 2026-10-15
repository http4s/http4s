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
import munit.CatsEffectSuite

class RuntimeDiagnosticsSuite extends CatsEffectSuite {
  private def report(error: Throwable): IO[Unit] =
    IO.executionContext.flatMap(context => IO(context.reportFailure(error)))

  test("captures cancellation finalizer reports without changing the outcome") {
    val cleanup = new RuntimeException("injected cleanup")
    RuntimeDiagnostics {
      IO.canceled.onCancel(IO.raiseError(cleanup)).start.flatMap(_.join).map { outcome =>
        assert(outcome.isCanceled)
      }
    }
  }

  test("passing tests retain their result despite runtime diagnostics") {
    RuntimeDiagnostics(report(new RuntimeException("diagnostic")).as(42)).assertEquals(42)
  }

  test("retains diagnostics alongside a test failure") {
    val primary = new RuntimeException("assertion")
    val diagnostic = new RuntimeException("cleanup")
    RuntimeDiagnostics(report(diagnostic) *> IO.raiseError[Unit](primary)).attempt.map {
      case Left(error) =>
        assert(error eq primary)
        assertEquals(error.getSuppressed.toList, List(diagnostic))
      case other => fail(s"expected both failures, got $other")
    }
  }

  test("does not duplicate the primary failure or traverse cyclic graphs") {
    val primary = new RuntimeException("assertion")
    val diagnostic = new AdapterFailure("cleanup", primary)
    primary.addSuppressed(diagnostic)
    RuntimeDiagnostics(report(primary) *> IO.raiseError[Unit](primary)).attempt
      .assertEquals(Left(primary))
  }

  test("restores the enclosing reporter after a capture") {
    val primary = new RuntimeException("assertion")
    val inner = new RuntimeException("inner")
    val outer = new RuntimeException("outer")
    RuntimeDiagnostics(
      RuntimeDiagnostics(report(inner)) *> report(outer) *> IO.raiseError[Unit](primary)
    ).attempt.map {
      case Left(error) =>
        assert(error eq primary)
        assertEquals(error.getSuppressed.toList, List(outer))
      case other => fail(s"expected only the outer diagnostic, got $other")
    }
  }
}

class RuntimeDiagnosticsIntegrationSuite extends RuntimeDiagnostics.Suite {
  test("suite capture preserves ordinary successful IO results") {
    IO.pure(42).assertEquals(42)
  }

  test("suite capture includes child fiber cancellation diagnostics") {
    IO.canceled
      .onCancel(IO.raiseError(new RuntimeException("expected cleanup")))
      .start
      .flatMap(_.join)
      .map(outcome => assert(outcome.isCanceled))
  }
}
