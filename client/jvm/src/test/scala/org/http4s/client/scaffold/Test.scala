/*
 * Copyright 2014 http4s.org
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

package org.http4s.client.scaffold

import cats.effect.std.Dispatcher
import cats.effect.{IO, IOApp}
import io.netty.channel._
import io.netty.handler.codec.http.HttpMethod._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http._
import org.http4s.client.scaffold.HandlerHelpers._

object Test extends IOApp.Simple {

  def run: IO[Unit] =
    Dispatcher[IO].flatMap(NettyTestServer[IO](1234, HandlersToNettyAdapter[IO](handlers), None, _)).useForever

  private val handlers: Map[(HttpMethod, String), Handler] = Map(
    (GET, "/simple") -> ((ctx, request) => sendResponse(ctx, request, OK, utf8Text("henlo"))),
    (GET, "/large") -> ((ctx, request) => sendResponse(ctx, request, OK, utf8Text("large" * 2048))),
    (GET, "/infinite") -> { (ctx, request) =>
      def go(): Unit =
        if (ctx.channel().isOpen()) {
          sendResponse(ctx, request, OK, utf8Text("large" * 2048))
            .addListener((_: ChannelFuture) => go())
        }

      go()
    },
    (POST, "/process-request-entity") -> ((ctx, request) =>
      sendResponse(ctx, request, OK, utf8Text("large" * 2048)))
  )
}