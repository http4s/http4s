/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.client

import cats.Applicative
import cats.effect._
import cats.syntax.functor._
import org.http4s._
import org.http4s.client._
import org.http4s.ember.core.Util.spanMessage
import org.typelevel.ci.CIString
import org.typelevel.keypool._
import org.typelevel.otel4s.context.propagation.TextMapUpdater
import org.typelevel.otel4s.semconv.attributes.HttpAttributes
import org.typelevel.otel4s.trace.SpanKind
import org.typelevel.otel4s.trace.Tracer

final class EmberClient[F[_]: Tracer] private[client] (
    private val client: Client[F],
    private val pool: KeyPool[F, RequestKey, EmberConnection[F]],
)(implicit F: MonadCancelThrow[F])
    extends DefaultClient[F] {
  import EmberClient.headersTMU

  /** The reason for this extra class. This allows you to see the present state
    * of the underlying Pool, without having access to the pool itself.
    *
    * The first element represents total connections in the pool, the second
    * is a mapping between the number of connections in the pool for each requestKey.
    */
  def state: F[(Int, Map[RequestKey, Int])] = pool.state

  def run(req: Request[F]): Resource[F, Response[F]] = {
    val tracer = Tracer[F]
    for {
      fullReqSpan <- {
        if (tracer.meta.isEnabled) {
          tracer // TODO: attributes
            .spanBuilder("Ember HTTP client request")
            .withSpanKind(SpanKind.Client)
            .addAttribute(HttpAttributes.HttpRequestMethod(req.method.name))
            .build
            .resource
        } else tracer.meta.noopSpanBuilder.build.resource
      }
      propagatedHeaders <- Resource.eval(tracer.propagate(req.headers)).mapK(fullReqSpan.trace)
      response <- client
        .run {
          spanMessage {
            tracer
              .span("Ember HTTP client send request body")
              .resource
              .mapK(fullReqSpan.trace)
          }(req.withHeaders(propagatedHeaders))
        }
        .map {
          spanMessage {
            tracer
              .span("Ember HTTP client receive response body")
              .resource
              .mapK(fullReqSpan.trace)
          }(_)
        }
        .mapK(fullReqSpan.trace)
    } yield response
  }

  override def defaultOnError(req: Request[F])(resp: Response[F])(implicit
      G: Applicative[F]
  ): F[Throwable] = F match {
    case concurrentF: Concurrent[F @unchecked] =>
      implicit val C = concurrentF
      resp.body.compile.drain.as(UnexpectedStatus(resp.status, req.method, req.uri))

    case _ =>
      F.pure(UnexpectedStatus(resp.status, req.method, req.uri))
  }
}

private object EmberClient {
  implicit private val headersTMU: TextMapUpdater[Headers] =
    (headers, key, value) => headers.put(Header.Raw(CIString(key), value))
}
