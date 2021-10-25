package org.http4s.client.scaffold

import io.netty.channel._
import io.netty.handler.codec.http._
import cats.effect.IO
import cats.effect.IOApp
import HandlerHelpers._
import io.netty.channel._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http._
import cats.effect.IO
import org.http4s.client.scaffold.HandlerHelpers._
import io.netty.handler.codec.http.HttpMethod._
import cats.effect.IOApp

object Test extends IOApp.Simple {
    
  def run: IO[Unit] = 
      NettyTestServer[IO](1234, new HandlersToNettyAdapter(handlers), None).useForever

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