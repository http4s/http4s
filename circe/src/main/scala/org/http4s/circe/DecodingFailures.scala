/*
 * Copyright 2015 http4s.org
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

package org.http4s.circe

import cats.data.NonEmptyList
import cats.syntax.show._
import io.circe.DecodingFailure

/** Wraps a list of decoding failures as an [[java.lang.Exception]] when using
  * [[accumulatingJsonOf]] to decode JSON messages.
  */
final case class DecodingFailures(failures: NonEmptyList[DecodingFailure]) extends Exception {
  override def getMessage: String = failures.iterator.map(_.show).mkString("\n")
}
