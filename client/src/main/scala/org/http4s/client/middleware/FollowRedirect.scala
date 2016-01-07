package org.http4s
package client
package middleware

import org.http4s._

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

              prepareLoop(
                req.copy(method = method, uri = nextUri, body = EmptyBody),
                redirects + 1
              )

            case _ => Task.now(dr)
          }
        }

        resp.status.code match {
          // We cannot be sure what will happen to the request body so we don't attempt to deal with it
          case 301 | 302 | 307 | 308 if req.body.isHalt =>
            dispose.flatMap(_ => doRedirect(req.method))

          // Often the result of a Post request where the body has been properly consumed
          case 303 =>
            dispose.flatMap(_ => doRedirect(Method.GET))

          case _ =>
            Task.now(dr)
        }
      }
    }

    client.copy(open = Service.lift(prepareLoop(_, 0)))
  }
}
