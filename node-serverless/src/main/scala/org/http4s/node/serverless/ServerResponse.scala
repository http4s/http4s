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

package org.http4s.node.serverless

import fs2.io.Writable

import scala.scalajs.js

@js.native
trait ServerResponse extends js.Object with Writable {
  def writeHead(
      statusCode: Int,
      statusMessage: String,
      headers: js.Dictionary[String]): ServerResponse = js.native
}
