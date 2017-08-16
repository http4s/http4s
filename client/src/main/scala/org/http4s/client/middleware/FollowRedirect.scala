package org.http4s
package client
package middleware

import org.http4s.Method._
import org.http4s.headers._
import org.http4s.syntax.string._
import org.http4s.util.CaseInsensitiveString
import scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process._
import scalaz.syntax.monad._
import scodec.bits.ByteVector

/** 
  * Client middleware to follow redirect responses.
  * 
  * A 301 or 302 response is followed by:
  * - a GET if the request was GET or POST
  * - a HEAD if the request was a HEAD
  * - the original request method and body if the body had no effects
  * - the redirect is not followed otherwise
  * 
  * A 303 response is followed by:
  * - a HEAD if the request was a HEAD
  * - a GET for all other methods
  * 
  * A 307 or 308 response is followed by:
  * - the original request method and body, if the body had no effects
  * - the redirect is not followed otherwise
  * 
  * Whenever we follow with a GET or HEAD, an empty body is sent, and
  * all payload headers defined in https://tools.ietf.org/html/rfc7231#section-3.3
  * are stripped.
  * 
  * If the response does not contain a valid Location header, the redirect is
  * not followed.
  *
  * Headers whose names match `sensitiveHeaderFilter` are not exposed when
  * redirecting to a different authority.
  */
object FollowRedirect {
  def apply(maxRedirects: Int, sensitiveHeaderFilter: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders)(client: Client): Client = {
    def prepareLoop(req: Request, redirects: Int): Task[DisposableResponse] = {
      client.open(req).flatMap { case dr @ DisposableResponse(resp, dispose) =>
        def redirectUri =
          resp.headers.get(Location).map { loc =>
            val uri = loc.uri
            // https://tools.ietf.org/html/rfc7231#section-7.1.2
            uri.copy(
              scheme = uri.scheme orElse req.uri.scheme,
              authority = uri.authority orElse req.uri.authority,
              fragment = uri.fragment orElse req.uri.fragment
            )
          }

        // We can only resubmit a body if it was not effectful.
        def pureBody = {
          req.body.unemit match {
            case (chunks, p) if p.isHalt =>
              Some(chunks.fold(ByteVector.empty)(_ ++ _))
            case _ =>
              None
          }
        }

        def dontRedirect =
          Task.now(dr)

        def stripSensitiveHeaders(nextUri: Uri): Request =
          if (req.uri.authority != nextUri.authority)
            req.transformHeaders(_.filterNot(h => sensitiveHeaderFilter(h.name)))
          else
            req
        
        def nextRequest(method: Method, nextUri: Uri, bodyOpt: Option[ByteVector]): Request =
          bodyOpt match {
            case Some(body) =>
              stripSensitiveHeaders(nextUri)
                .withMethod(method)
                .withUri(nextUri)
                // Assume that all the headers can be propagated
                // TODO we should be filtering sensitive headers if
                // we're redirecting to a new host
                .withBodyStream(emit(body))
            case None =>
              stripSensitiveHeaders(nextUri)
                .withMethod(method)
                .withUri(nextUri)
                .withEmptyBody
          }

        def doRedirect(method: Method) = {
          if (redirects < maxRedirects) {
            // If we get a redirect response without a location, then there is
            // nothing to redirect.
            redirectUri.fold(dontRedirect) { nextUri =>
              // We can only redirect safely if there is no body or if we've
              // verified that the body is pure.
              val nextReq = method match {
                case GET | HEAD =>
                  Some(nextRequest(method, nextUri, None))
                case _ =>
                  pureBody.map(body => nextRequest(method, nextUri, Some(body)))
              }
              nextReq.fold(dontRedirect)(dr.dispose >> prepareLoop(_, redirects + 1))
            }
          }
          else dontRedirect
        }

        resp.status.code match {
          case 301 | 302 =>
            req.method match {
              case POST =>
                // "For historical reasons, a user agent MAY change the request method
                // from POST to GET for the subsequent request." -- RFC 7231
                //
                // This is common practice, so we do.
                //
                // TODO In a future version, configure this behavior through a
                // redirect config.
                doRedirect(GET)

              case m => 
                doRedirect(m)
            }

          case 303 =>
            // "303 (See Other) status code indicates that the server is
            // redirecting the user agent to a different resource, as indicated
            // by a URI in the Location header field, which is intended to
            // provide an indirect response to the original request.  A user
            // agent can perform a retrieval request targeting that URI (a GET
            // or HEAD request if using HTTP)" -- RFC 7231
            doRedirect(req.method match {
              case HEAD => HEAD
              case _ => GET
            })

          case 307 | 308 =>
            // "Note: This status code is similar to 302 (Found), except that
            // it does not allow changing the request method from POST to GET.
            // This specification defines no equivalent counterpart for 301
            // (Moved Permanently) ([RFC7238], however, defines the status code
            // 308 (Permanent Redirect) for this purpose). These status codes
            // may not change the method." -- RFC 7231
            //
            doRedirect(req.method)

          case _ =>
            Task.now(dr)
        }
      }
    }

    client.copy(open = Service.lift(prepareLoop(_, 0)))
  }
}
