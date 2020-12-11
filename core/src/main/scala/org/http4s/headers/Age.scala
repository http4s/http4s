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

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderer, Writer}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try

object Age extends HeaderKey.Internal[Age] with HeaderKey.Singleton {
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

  override def parse(s: String): ParseResult[Age] =
    HttpHeaderParser.AGE(s)
}

/** Constructs an Age header.
  *
  * The value of this field is a positive number of seconds (in decimal) with an estimate of the amount of time since the response
  *
  * @param age age of the response
  */
sealed abstract case class Age(age: Long) extends Header.Parsed {
  val key = Age

  override val value = Renderer.renderString(age)

  override def renderValue(writer: Writer): writer.type = writer.append(value)

  def duration: Option[FiniteDuration] = Try(age.seconds).toOption

  def unsafeDuration: FiniteDuration = age.seconds
}
