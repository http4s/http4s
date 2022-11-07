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
import org.typelevel.ci._

import scala.concurrent.duration.DurationLong
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/** The `Access-Control-Max-Age` header. */
trait `Access-Control-Max-Age`

object `Access-Control-Max-Age` {

  val name: CIString = ci"Access-Control-Max-Age"

  /** A value of -1 of the age parameter will disable caching. */
  case object NoCaching extends `Access-Control-Max-Age`

  /** The value of this field indicates how long the results of a preflight request (that is the information contained in the Access-Control-Allow-Methods and [[Access-Control-Allow-Headers]] headers) can be cached.
    *
    * @param age age of the response (in seconds)
    */
  final case class Cache private (age: Long) extends `Access-Control-Max-Age` { // scalafix:ok Http4sGeneralLinters.nonValidatingCopyConstructor; bincompat until 1.0
    def duration: Option[FiniteDuration] = Try(age.seconds).toOption
    def unsafeDuration: FiniteDuration = age.seconds
  }

  def fromLong(age: Long): ParseResult[`Access-Control-Max-Age`] =
    if (age >= 0)
      ParseResult.success(Cache.apply(age))
    else if (age == -1) {
      ParseResult.success(NoCaching)
    } else {
      ParseResult.fail(
        "Invalid age value",
        s"Access-Control-Max-Age param $age must be greater or equal to 0 seconds or -1",
      )
    }

  def unsafeFromDuration(age: FiniteDuration): `Access-Control-Max-Age` =
    fromLong(age.toSeconds).fold(throw _, identity)

  def unsafeFromLong(age: Long): `Access-Control-Max-Age` =
    fromLong(age).fold(throw _, identity)

  def parse(s: String): ParseResult[`Access-Control-Max-Age`] =
    ParseResult.fromParser(parser, "Invalid Access-Control-Max-Age header")(s)

  private[http4s] val parser = AdditionalRules.Long.map(unsafeFromLong)

  implicit val headerInstance: Header[`Access-Control-Max-Age`, Header.Single] =
    Header.createRendered(
      name,
      {
        case Cache(age) => age
        case NoCaching => -1
      },
      parse,
    )
}
