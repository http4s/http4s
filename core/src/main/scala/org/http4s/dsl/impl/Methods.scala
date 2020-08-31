/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
