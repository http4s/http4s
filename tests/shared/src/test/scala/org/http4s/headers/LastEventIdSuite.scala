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
package headers

import org.http4s.ServerSentEvent._
import org.scalacheck.Arbitrary._
import org.scalacheck._

class LastEventIdSuite extends HeaderLaws {
  implicit val arbLastEventId: Arbitrary[`Last-Event-Id`] =
    Arbitrary(for {
      id <- arbitrary[String]
      if !id.contains("\n") && !id.contains("\r")
    } yield `Last-Event-Id`(EventId(id)))

//  checkAll("Last-Event-Id", headerLaws(`Last-Event-Id`))
}
