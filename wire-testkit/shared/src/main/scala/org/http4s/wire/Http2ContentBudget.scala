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

/** Pure accounting shared by content observers. The caller chooses what is retained:
  * decoded content for HPACK, or full wire frames for message observations.
  * Reserve before appending bytes; a terminal frame may consume the last allowance.
  */
private[wire] object Http2ContentBudget {
  final case class Policy(frames: Int, bytes: Long, frameLimit: String, byteLimit: String) {
    def initial: State = new State(this, frames, 0L)
  }
  final class State private[Http2ContentBudget] (
      val policy: Policy,
      val remaining: Int,
      val retained: Long,
  ) {
    def beforeRead: Either[WireObservationLimit, Unit] =
      if (remaining > 0) Right(()) else Left(new WireObservationLimit(policy.frameLimit))

    def reserve(additional: Long): Either[WireObservationLimit, State] =
      beforeRead.flatMap { _ =>
        if (
          additional < 0L || retained < 0L || retained > policy.bytes ||
          additional > policy.bytes - retained
        )
          Left(new WireObservationLimit(policy.byteLimit))
        else Right(new State(policy, remaining - 1, retained + additional))
      }
  }
}
