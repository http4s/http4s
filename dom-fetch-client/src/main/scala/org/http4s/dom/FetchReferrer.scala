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

package org.http4s.dom

import org.http4s.Uri
import org.http4s.util.Writer
import org.http4s.util.Renderable

/** Fetch's `RequestInit.referrer` parameter can be one of:
  *   - Same-origin URL
  *   - "about:client"
  *   - the empty string
  *
  * To ensure same-origin URLs, we only allow relative URLs in the form of [[Uri.Path]].
  *
  * See https://fetch.spec.whatwg.org/#ref-for-dom-request-referrer%E2%91%A0
  */
sealed abstract class FetchReferrer extends Renderable
object FetchReferrer {
  case object NoReferrer extends FetchReferrer {
    override def render(writer: Writer): writer.type = writer << ""
  }
  case object Client extends FetchReferrer {
    override def render(writer: Writer): writer.type = writer << "about:client"
  }
  final case class Path(path: Uri.Path) extends FetchReferrer {
    override def render(writer: Writer): writer.type = writer << path.renderString
  }
}
