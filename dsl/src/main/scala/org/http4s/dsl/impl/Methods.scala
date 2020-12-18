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
  val GET: Method.GET.type = Method.GET
  val HEAD: Method.HEAD.type = Method.HEAD
  val POST: Method.POST.type = Method.POST
  val PUT: Method.PUT.type = Method.PUT
  val DELETE: Method.DELETE.type = Method.DELETE
  val CONNECT: Method.CONNECT.type = Method.CONNECT
  val OPTIONS: Method.OPTIONS.type = Method.OPTIONS
  val TRACE: Method.TRACE.type = Method.TRACE
  val PATCH: Method.PATCH.type = Method.PATCH
}
