/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.ember.client

import fs2.io.tcp._
import org.http4s.client.RequestKey

private[client] final case class RequestKeySocket[F[_]](
    socket: Socket[F],
    requestKey: RequestKey
)
