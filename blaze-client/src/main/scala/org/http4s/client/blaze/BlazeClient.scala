package org.http4s
package client.blaze

import org.http4s.blaze.pipeline.Command
import org.http4s.client.Client

import scalaz.concurrent.Task
import scalaz.stream.Process.eval_
import scalaz.{-\/, \/-}

/** Blaze client implementation */
final class BlazeClient(manager: ConnectionManager, maxRedirects: Int) extends Client {
  import BlazeClient.redirectCount

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = manager.shutdown()

  override def prepare(req: Request): Task[Response] = {
    val t = buildRequestTask(req)

    if (maxRedirects > 0) {

      t.flatMap { resp =>

        def doRedirect(method: Method): Task[Response] = {
          val redirects = resp.attributes.get(redirectCount).getOrElse(0)

          resp.headers.get(headers.Location) match {
            case Some(headers.Location(uri)) if redirects < maxRedirects =>
              // https://tools.ietf.org/html/rfc7231#section-7.1.2
              val nextUri = uri.copy(
                scheme = uri.scheme orElse req.uri.scheme,
                authority = uri.authority orElse req.uri.authority,
                fragment = uri.fragment orElse req.uri.fragment
              )

              val newattrs = resp.attributes.put(redirectCount, redirects + 1)
              prepare(req.copy(uri = nextUri, attributes = newattrs, body = EmptyBody))

            case _ =>
              Task.now(resp)
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
    else t
  }

  private def buildRequestTask(req: Request): Task[Response] = {
    def tryClient(client: BlazeClientStage, freshClient: Boolean): Task[Response] = {
        client.runRequest(req).attempt.flatMap {
          case \/-(r)    =>
            val recycleProcess = eval_(Task.delay {
              if (!client.isClosed()) {
                manager.recycleClient(req, client)
              }
            })
            Task.now(r.copy(body = r.body ++ recycleProcess, attributes = req.attributes))

          case -\/(Command.EOF) if !freshClient =>
            manager.getClient(req, fresh = true).flatMap(tryClient(_, true))

          case -\/(e) =>
            if (!client.isClosed()) {
              client.shutdown()
            }
            Task.fail(e)
        }
    }

    manager.getClient(req, fresh = false).flatMap(tryClient(_, false))
  }
}

object BlazeClient {
  private[BlazeClient] val redirectCount = AttributeKey[Int]("redirectCount")
}
