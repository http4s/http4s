/*
 * Copyright 2013 http4s.org
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

package org.http4s

import cats.kernel.laws.discipline.OrderTests
import org.http4s.Status._
import org.http4s.laws.discipline.arbitrary._
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop.propBoolean

import java.nio.charset.StandardCharsets
import scala.annotation.nowarn

class StatusSpec extends StatusDeprecatedSpec {
  checkAll("Status", OrderTests[Status].order)

  test("Statuses should not be equal if their codes are not") {
    forAll { (s1: Status, s2: Status) =>
      (s1.code != s2.code) ==> (s1 != s2)
    }
  }

  test("Statuses should be equal if their codes are") {
    forAll(genValidStatusCode) { i =>
      val s1: Status = getStatus(i, "a reason")
      val s2: Status = getStatus(i, "another reason")
      s1 == s2
    }
  }

  test("Statuses should be ordered by their codes") {
    forAll { (s1: Status, s2: Status) =>
      (s1.code < s2.code) ==> (s1 < s2)
    }
  }

  test("Statuses should have the appropriate response classes") {
    forAll(genValidStatusCode) { code =>
      val expected = code / 100 match {
        case 1 => Informational
        case 2 => Successful
        case 3 => Redirection
        case 4 => ClientError
        case 5 => ServerError
      }
      fromInt(code).fold(_ => false, s => s.responseClass == expected)
    }
  }

  test("The collection of registered statuses should contain 62 standard ones") {
    assertEquals(Status.registered.size, 62)
  }

  test("The collection of registered statuses should not contain any custom statuses") {
    getStatus(371)
    assertEquals(Status.registered.size, 62)
  }

  test("Finding a status by code should fail if the code is not in the range of valid codes") {
    forAll(Gen.choose(Int.MinValue, 99)) { i =>
      fromInt(i).isLeft
    }
  }

  test("Finding a status by code should fail if the code is not in the range of valid codes") {
    forAll(Gen.choose(600, Int.MaxValue)) { i =>
      fromInt(i).isLeft
    }
  }

  test(
    "Finding a status by code should succeed if the code is in the valid range, but not a standard code"
  ) {
    assert(fromInt(371).fold(_ => false, s => s.reason == ""))
    assert(fromInt(482).isRight)
  }

  test(
    "Finding a status by code should yield a status with the standard reason for a standard code"
  ) {
    assertEquals(getStatus(NotFound.code).reason, "Not Found")
  }

  test(
    "Finding a status by code and reason should succeed if the code is in the valid range, but not a standard code"
  ) {
    val s1 = getStatus(371, "some reason")
    assertEquals(s1.code, 371)
    assertEquals(s1.reason, "some reason")
  }

  test(
    "Finding a status by code and reason should succeed for a standard code and nonstandard reason, without replacing the default reason"
  ) {
    assertEquals(
      getStatus(NotFound.code, "My dog ate my homework").reason,
      "My dog ate my homework",
    )

    assertEquals(getStatus(NotFound.code).reason, "Not Found")
  }

  test("all known status have a reason") {
    Status.registered.foreach { status =>
      assert(status.renderString.drop(4).nonEmpty, status.renderString)
    }
  }

  def isSanitized(s: Status): Boolean =
    s.renderString
      .getBytes(StandardCharsets.ISO_8859_1)
      .forall(b => b == ' ' || b == '\t' || (b >= 0x21 && b <= 0x7e) || ((b & 0xff) >= 0x80))

  test("rendering sanitizes statuses") {
    forAll((s: Status) => isSanitized(s))
  }

  test("#5736 regression") {
    val reason = "椗찦铝宏ýುؽ汃붒ᷖ鯣츑䭚䩰ܨ瀏ĵꎉ쎿뎯뾥뛾틨ޡ囨"
    val s = Status.fromInt(200).toOption.get.withReason(reason): @nowarn("cat=deprecation")
    assert(isSanitized(s))
  }

  private def getStatus(code: Int) =
    fromInt(code) match {
      case Right(s) => s
      case Left(t) => throw t
    }
}

@nowarn("cat=deprecation")
class StatusDeprecatedSpec extends Http4sSuite {
  test("Finding a status by code and reason should fail if the code is not in the valid range") {
    assert(fromIntAndReason(17, "a reason").isLeft)
  }

  test("Finding a status by code and reason should succeed for a standard code and reason") {
    assertEquals(getStatus(NotFound.code, "Not Found").reason, "Not Found")
  }

  protected def getStatus(code: Int, reason: String): Status =
    fromIntAndReason(code, reason) match {
      case Right(s) => s
      case Left(t) => throw t
    }
}
