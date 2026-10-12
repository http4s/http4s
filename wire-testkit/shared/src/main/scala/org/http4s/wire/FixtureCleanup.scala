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

import cats.effect.Async

/** Exchange-local boundary for fixture-owned effect cleanup.
  *
  * Apply at finalizer construction, inside `onCancel`, `guaranteeCase`, or a resource's
  * release callback. Failures escaping the complete cleanup effect are retained before
  * cancellation can hide them. Recovery within that effect remains recovery; handling
  * errors outside this boundary does not erase a failure already reported to the adapter.
  * The effect is deferred, so synchronous construction failures are retained as well.
  * This boundary neither changes the original exit case nor runs cleanup itself.
  */
final class FixtureCleanup[F[_]] private[wire] (capture: F[Unit] => F[Unit])(implicit
    F: Async[F]
) {
  def apply(finalize: => F[Unit]): F[Unit] = capture(F.defer(finalize))
}
