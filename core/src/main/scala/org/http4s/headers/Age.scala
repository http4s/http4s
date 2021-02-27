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

import org.http4s.parser.AdditionalRules
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try
import org.typelevel.ci.CIString

object Age {
  private class AgeImpl(age: Long) extends Age(age)

  def fromLong(age: Long): ParseResult[Age] =
    if (age >= 0)
      ParseResult.success(new AgeImpl(age))
    else
      ParseResult.fail("Invalid age value", s"Age param $age must be more or equal to 0 seconds")

  def unsafeFromDuration(age: FiniteDuration): Age =
    fromLong(age.toSeconds).fold(throw _, identity)

  def unsafeFromLong(age: Long): Age =
    fromLong(age).fold(throw _, identity)

  def parse(s: String): ParseResult[Age] =
    ParseResult.fromParser(parser, "Invalid Age header")(s)
  private[http4s] val parser = AdditionalRules.NonNegativeLong.map(unsafeFromLong)

  implicit val headerInstance: v2.Header[Age, v2.Header.Single] =
    v2.Header.createRendered(
      CIString("Age"),
      _.age,
      ParseResult.fromParser(parser, "Invalid Age header")
    )
}

/** Constructs an Age header.
  *
  * The value of this field is a positive number of seconds (in decimal) with an estimate of the amount of time since the response
  *
  * @param age age of the response
  */
sealed abstract case class Age(age: Long) {
  def value = age.toString

  def duration: Option[FiniteDuration] = Try(age.seconds).toOption

  def unsafeDuration: FiniteDuration = age.seconds
}
