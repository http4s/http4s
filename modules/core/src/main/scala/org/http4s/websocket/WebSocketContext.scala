/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.websocket

import org.http4s.{Headers, Response}

final case class WebSocketContext[F[_]](
    webSocket: WebSocket[F],
    headers: Headers,
    failureResponse: F[Response[F]])
