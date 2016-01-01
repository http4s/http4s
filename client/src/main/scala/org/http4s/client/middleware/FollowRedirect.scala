package org.http4s
package client
package middleware

import org.http4s._

import scalaz.concurrent.Task

/** Follow redirect responses */
object FollowRedirect {

  def apply(maxRedirects: Int)(client: Client): Client = new Client {
    /** Shutdown this client, closing any open connections and freeing resources */
    override def shutdown(): Task[Unit] = client.shutdown()

    /** Prepare a single request
      * @param req [[Request]] containing the headers, URI, etc.
      * @return Task which will generate the Response
      */
    override def open(req: Request): Task[DisposableResponse] =
      loop(req, 0)

    private def loop(req: Request, redirects: Int): Task[DisposableResponse] = {
      client.open(req).flatMap { dr =>
        def doRedirect(method: Method): Task[DisposableResponse] = {
          dr.response.headers.get(headers.Location) match {
            case Some(headers.Location(uri)) if redirects < maxRedirects =>
              // https://tools.ietf.org/html/rfc7231#section-7.1.2
              val nextUri = uri.copy(
                scheme = uri.scheme orElse req.uri.scheme,
                authority = uri.authority orElse req.uri.authority,
                fragment = uri.fragment orElse req.uri.fragment
              )

              loop(
                req.copy(method = method, uri = nextUri, body = EmptyBody),
                redirects + 1
              )

            case _ => Task.now(dr)
          }
        }

        dr.response.status.code match {
          // We cannot be sure what will happen to the request body so we don't attempt to deal with it
          case 301 | 302 | 307 | 308 if req.body.isHalt =>
            dr.dispose.flatMap(_ => doRedirect(req.method))

          // Often the result of a Post request where the body has been properly consumed
          case 303 =>
            dr.dispose.flatMap(_ => doRedirect(Method.GET))

          case _ =>
            Task.now(dr)
        }
      }
    }
  }
}
