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

package org.http4s.dsl.impl

import org.http4s.Method

trait Methods {
  val GET: Method.Get.type = Method.Get
  val HEAD: Method.Head.type = Method.Head
  val POST: Method.Post.type = Method.Post
  val PUT: Method.Put.type = Method.Put
  val DELETE: Method.Delete.type = Method.Delete
  val CONNECT: Method.Connect.type = Method.Connect
  val OPTIONS: Method.Options.type = Method.Options
  val TRACE: Method.Trace.type = Method.Trace
  val PATCH: Method.Patch.type = Method.Patch
}
