package org.http4s
package client
package middleware

import scalaz.Kleisli
import scalaz.concurrent.Task

/** Follow redirect responses */
object FollowRedirect {

  def apply(maxRedirects: Int)(client: Client): Client = {
    def prepareLoop(req: Request, redirects: Int): Task[DisposableResponse] = {
      client.open(req).flatMap { case dr @ DisposableResponse(resp, dispose) =>
        def doRedirect(method: Method) = {
          resp.headers.get(headers.Location) match {
            case Some(headers.Location(uri)) if redirects < maxRedirects =>
              // https://tools.ietf.org/html/rfc7231#section-7.1.2
              val nextUri = uri.copy(
                scheme = uri.scheme orElse req.uri.scheme,
                authority = uri.authority orElse req.uri.authority,
                fragment = uri.fragment orElse req.uri.fragment
              )

              // We're following a redirect, so we need to dispose of the last response
              dispose.flatMap { _ =>
                prepareLoop(
                  req.copy(method = method, uri = nextUri, body = EmptyBody),
                  redirects + 1
                )
              }

            case _ => Task.now(dr)
          }
        }

        resp.status.code match {
          case 301 | 302 =>
            doRedirect(req.method match {
              case Method.POST => Method.GET
              case m => m
            })

          case 303 =>
            // A 303 is intended to call a HEAD or a GET.  We'll preserve
            // HEAD, and send through GET otherwise.
            doRedirect(req.method match {
              case Method.HEAD => Method.HEAD
              case m => Method.GET
            })

          case 307 | 308 =>
            // These status codes may not change the method.
            doRedirect(req.method)

          case _ =>
            Task.now(dr)
        }
      }
    }

    client.copy(open = Service.lift(prepareLoop(_, 0)))
  }
}
