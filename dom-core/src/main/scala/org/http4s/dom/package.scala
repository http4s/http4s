/*
 * Copyright 2021 http4s.org
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

import org.scalajs.dom.experimental.{Headers => DomHeaders}

import scala.scalajs.js.JSConverters._

package object dom {

  def toDomHeaders(headers: Headers): DomHeaders =
    new DomHeaders(
      headers.headers.view
        .map { case Header.Raw(name, value) =>
          name.toString -> value
        }
        .toMap
        .toJSDictionary)

  def fromDomHeaders(headers: DomHeaders): Headers =
    Headers(
      headers.toIterable.map { header =>
        header(0) -> header(1)
      }.toList
    )

}
