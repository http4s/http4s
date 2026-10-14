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

class Http2ContentBudgetSuite extends munit.FunSuite {
  private val policy = Http2ContentBudget.Policy(2, 8L, "frames", "bytes")

  test("terminal content may exactly exhaust both bounds") {
    val full = policy.initial
      .reserve(3)
      .flatMap(_.reserve(5))
      .fold(error => fail(s"unexpected failure: $error"), identity)
    assertEquals(full.retained, 8L)
    assert(full.beforeRead.isLeft)
    assert(full.reserve(0).isLeft)
  }

  test("wire overhead and decoded content can be charged independently") {
    val decoded = policy.initial.reserve(8)
    val wire = policy.initial.reserve(8 + 9)
    assert(decoded.isRight)
    assert(wire.isLeft)
  }

  test("negative and overflowing reservations fail without changing the prior budget") {
    val previous =
      policy.initial.reserve(3).fold(error => fail(s"unexpected failure: $error"), identity)
    for (amount <- Vector(-1L, 6L, Long.MaxValue)) assert(previous.reserve(amount).isLeft)
    assertEquals(
      previous.reserve(5).fold(error => fail(s"unexpected failure: $error"), identity).retained,
      8L,
    )
    val large = policy
      .copy(bytes = Long.MaxValue)
      .initial
      .reserve(Long.MaxValue)
      .fold(error => fail(s"unexpected failure: $error"), identity)
    assert(large.reserve(1).isLeft)
  }

  test("empty frames still consume the frame allowance") {
    assert(policy.initial.reserve(0).flatMap(_.reserve(0)).flatMap(_.beforeRead).isLeft)
    assert(policy.copy(frames = 0).initial.beforeRead.isLeft)
    assert(policy.copy(bytes = 0).initial.reserve(0).isRight)
  }
}
