/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

final case class ServerSoftware(
    product: String,
    productVersion: Option[String] = None,
    comment: Option[String] = None)

object ServerSoftware {
  val Unknown = ServerSoftware("Unknown", None, None)
}
