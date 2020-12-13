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

package org.http4s.metrics
/*
import cats.effect.IO
import cats.syntax.all._
import java.util.UUID
import org.http4s._
import org.scalacheck.{Arbitrary, Gen}
import MetricsOps.classifierFMethodWithOptionallyExcludedPath

object MetricsOpsSpec {

  private implicit val arbUUID: Arbitrary[UUID] =
    Arbitrary(Gen.uuid)
}

class MetricsOpsSpec extends Http4sSpec {

  import MetricsOpsSpec.arbUUID

  "classifierFMethodWithOptionallyExcludedPath" should {
    "properly exclude UUIDs" in prop {
      (method: Method, uuid: UUID, excludedValue: String, separator: String) =>
        val request: Request[IO] = Request[IO](
          method = method,
          uri = Uri.unsafeFromString(s"/users/$uuid/comments")
        )

        val excludeUUIDs: String => Boolean = { (str: String) =>
          Either
            .catchOnly[IllegalArgumentException](UUID.fromString(str))
            .isRight
        }

        val classifier: Request[IO] => Option[String] =
          classifierFMethodWithOptionallyExcludedPath(
            exclude = excludeUUIDs,
            excludedValue = excludedValue,
            pathSeparator = separator
          )

        val result: Option[String] =
          classifier(request)

        val expected: Option[String] =
          Some(
            method.name +
              separator +
              "users" +
              separator +
              excludedValue +
              separator +
              "comments"
          )

        result ==== expected
    }
    "return '$method' if the path is '/'" in prop { (method: Method) =>
      val request: Request[IO] = Request[IO](
        method = method,
        uri = uri"""/"""
      )

      val classifier: Request[IO] => Option[String] =
        classifierFMethodWithOptionallyExcludedPath(
          _ => true,
          "*",
          "_"
        )

      val result: Option[String] =
        classifier(request)

      result ==== Some(method.name)
    }
  }

}
 */
