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
    override def prepare(req: Request): Task[Response] = prepareLoop(req, 0)

    private def prepareLoop(req: Request, redirects: Int): Task[Response] = {
      val t = client.prepare(req)
      t.flatMap { resp =>

        def doRedirect(method: Method): Task[Response] = {
          resp.headers.get(headers.Location) match {
            case Some(headers.Location(uri)) if redirects < maxRedirects =>
              // https://tools.ietf.org/html/rfc7231#section-7.1.2
              val nextUri = uri.copy(
                scheme = uri.scheme orElse req.uri.scheme,
                authority = uri.authority orElse req.uri.authority,
                fragment = uri.fragment orElse req.uri.fragment
              )

              prepareLoop(req.copy(uri = nextUri, body = EmptyBody), redirects + 1)

            case _ => Task.now(resp)
          }
        }

        resp.status.code match {
          // We cannot be sure what will happen to the request body so we don't attempt to deal with it
          case 301 | 302 | 307 | 308 if req.body.isHalt => doRedirect(req.method)

          // Often the result of a Post request where the body has been properly consumed
          case 303 => doRedirect(Method.GET)

          case _ => Task.now(resp)
        }
      }
    }
  }
}
