package org.http4s.client.scaffold

import cats.effect.kernel.Async
import org.http4s.HttpRoutes
import cats.effect.std.Dispatcher
import io.netty.channel.ChannelInboundHandler
import cats.effect.kernel.Resource

class RoutesToNettyAdapter[F[_]](routes: HttpRoutes[F], dispatcher: Dispatcher[F]
  )(implicit F: Async[F]) 
  extends ChannelInboundHandler {
        // TODO
        val run = for {
          method <- Method.fromString(exchange.getRequestMethod()).liftTo[F]
          uri <- http4s.Uri.fromString(exchange.getRequestURI().toString()).liftTo[F]
          headers = http4s.Headers(
            exchange.getRequestHeaders().asScala.toList.flatMap { case (k, vs) =>
              vs.asScala.toList.map { v =>
                (k -> v): http4s.Header.ToRaw
              }
            }): @nowarn("cat=deprecation")
          body = fs2.io.readInputStream(exchange.getRequestBody().pure[F], 8192)
          request = http4s.Request(method, uri, headers = headers, body = body)
          response <- routes.run(request).value
          _ <- response.fold(F.unit) { res =>
            F.delay {
              res.headers.foreach { h =>
                if (h.name =!= http4s.headers.`Content-Length`.name)
                  exchange.getResponseHeaders.add(h.name.toString, h.value)
              }
            } *> F.blocking {
              // com.sun.net.httpserver warns on nocontent with a content lengt that is not -1
              val contentLength =
                if (res.status.code == http4s.Status.NoContent.code) -1L
                else res.contentLength.getOrElse(0L)
              exchange.sendResponseHeaders(res.status.code, contentLength)
            } *>
              res.body
                .through(fs2.io
                  .writeOutputStream[F](exchange.getResponseBody.pure[F]))
                .compile
                .drain
          }
        } yield ()
        dispatcher.unsafeRunAndForget(run.guarantee(F.blocking(exchange.close())))
}

object RoutesToNettyAdapter {
  def apply[F[_]](routes: HttpRoutes[F])(implicit F: Async[F]): Resource[F, RoutesToNettyAdapter[F]] =
    Dispatcher[F].map(dispatcher => new RoutesToNettyAdapter(routes, dispatcher))
}