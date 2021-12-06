/*
 * Copyright 2014 http4s.org
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
package client

import org.http4s.laws.discipline.arbitrary._
import org.http4s.syntax.literals._
import org.scalacheck.Prop

class UnexpectedStatusSuite extends Http4sSuite {

  test("UnexpectedStatus should include status and original request in message") {
    val e = UnexpectedStatus(Status.NotFound, Method.GET, uri"www.google.com")
    assertEquals(
      e.getMessage(),
      "unexpected HTTP status: 404 Not Found for request GET www.google.com",
    )
  }

  property("UnexpectedStatus should not return null") {
    Prop.forAll { (status: Status) =>
      val e = UnexpectedStatus(status, Method.GET, uri"www.google.it")
      Option(e.getMessage()).isDefined
    }
  }
}
