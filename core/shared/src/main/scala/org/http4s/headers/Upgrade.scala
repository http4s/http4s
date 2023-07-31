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

import cats.data.NonEmptyList
import cats.parse.Parser
import org.http4s.internal.parsing.CommonRules
import org.http4s.internal.parsing.Rfc2616
import org.typelevel.ci._

object Upgrade extends HeaderCompanion[Upgrade]("Upgrade") {

  def apply(head: Protocol, tail: Protocol*): Upgrade =
    apply(NonEmptyList(head, tail.toList))

  private[http4s] val parser = {
    import Parser.char
    val protocol = (Rfc2616.token ~ (char('/') *> Rfc2616.token).?).map { case (name, version) =>
      Protocol(CIString(name), version.map(CIString(_)))
    }
    CommonRules.headerRep1(protocol).map { (xs: NonEmptyList[Protocol]) =>
      Upgrade(xs.head, xs.tail: _*)
    }
  }

  implicit val headerInstance: Header[Upgrade, Header.Single] =
    createRendered(_.values.map(_.toString))
}

final case class Upgrade(values: NonEmptyList[Protocol])
