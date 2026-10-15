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

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** Buffer runtime reports from joined fixture fibers until the test outcome is known.
  * Deliberately failing finalizers and peer teardown can report errors even when the
  * test's outcome assertions pass. Keep those diagnostics on failures, without changing
  * the effects or globally silencing the runtime reporter.
  */
private[wire] object RuntimeDiagnostics {
  abstract class Suite extends CatsEffectSuite {
    override def munitValueTransforms: List[ValueTransform] = {
      val underlying = super.munitValueTransforms
      new ValueTransform(
        "runtime diagnostics",
        { case run: IO[_] =>
          underlying.iterator
            .map(_(RuntimeDiagnostics(run)))
            .collectFirst { case Some(result) =>
              result
            }
            .getOrElse(Future.failed(new IllegalStateException("Missing MUnit IO transform")))
        },
      ) :: underlying
    }
  }

  def apply[A](run: IO[A]): IO[A] =
    IO.executionContext.flatMap { underlying =>
      val reports = new AtomicReference(Vector.empty[Throwable])
      val context = new ExecutionContext {
        def execute(runnable: Runnable): Unit = underlying.execute(runnable)
        def reportFailure(error: Throwable): Unit = {
          reports.updateAndGet(_ :+ error)
          ()
        }
      }
      run.evalOn(context).handleErrorWith { primary =>
        IO.delay {
          reports.get().foreach { diagnostic =>
            if ((diagnostic ne primary) && !primary.getSuppressed.exists(_ eq diagnostic))
              primary.addSuppressed(diagnostic)
          }
        } *> IO.raiseError(primary)
      }
    }
}
