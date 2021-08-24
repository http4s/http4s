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

sealed abstract class FetchReferrer extends Renderable
object FetchReferrer {
  case object NoReferrer extends FetchReferrer {
    override def render(writer: Writer): writer.type = writer << "no-referrer"
  }
  case object Client extends FetchReferrer {
    override def render(writer: Writer): writer.type = writer << "client"
  }
  final case class URL(uri: Uri) extends FetchReferrer {
    override def render(writer: Writer): writer.type = writer << uri.renderString
  }
}
