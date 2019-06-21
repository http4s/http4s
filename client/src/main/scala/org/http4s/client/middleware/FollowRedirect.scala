package org.http4s
package client
package middleware

import cats.effect._
import cats.implicits._
import org.http4s.Method._
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString
import _root_.io.chrisdavenport.vault._

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
  def apply[F[_]](
      maxRedirects: Int,
      sensitiveHeaderFilter: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders)(
      client: Client[F])(implicit F: Bracket[F, Throwable]): Client[F] = {

    def nextRequest(req: Request[F], uri: Uri, method: Method, cookies: List[ResponseCookie]): Request[F] = {
      // https://tools.ietf.org/html/rfc7231#section-7.1.
      val nextUri = uri.copy(
        scheme = uri.scheme.orElse(req.uri.scheme),
        authority = uri.authority.orElse(req.uri.authority),
        fragment = uri.fragment.orElse(req.uri.fragment)
      )

      def stripSensitiveHeaders(req: Request[F]): Request[F] =
        if (req.uri.authority != nextUri.authority)
          req.transformHeaders(_.filterNot(h => sensitiveHeaderFilter(h.name)))
        else
          req

      def propagateCookies(req: Request[F]): Request[F] =
        if (req.uri.authority == nextUri.authority)
          cookies.foldLeft(req) {
            case (nextReq, cookie) => nextReq.addCookie(cookie.name, cookie.content)
          }
        else
          req

      def clearBodyFromGetHead(req: Request[F]): Request[F] =
        method match {
          case GET | HEAD => req.withEmptyBody
          case _ => req
        }

      clearBodyFromGetHead(propagateCookies(stripSensitiveHeaders(req)).withMethod(method).withUri(nextUri))
    }

    def prepareLoop(req: Request[F], redirects: Int): Resource[F, Response[F]] =
      client.run(req).flatMap { resp =>
        (methodForRedirect(req, resp), resp.headers.get(Location)) match {
          case (Some(method), Some(loc)) if redirects < maxRedirects =>
            val nextReq = nextRequest(req, loc.uri, method, resp.cookies)
            prepareLoop(nextReq, redirects + 1).map { response =>
              // prepend because `prepareLoop` is recursive
              response.withAttribute(redirectUrisKey, nextReq.uri +: getRedirectUris(response))
            }
          case _ => resp.pure[Resource[F, ?]]
          // IF the response is missing the Location header, OR there is no method to redirect,
          // OR we have exceeded max number of redirections, THEN we redirect no more
        }
      }

    Client(prepareLoop(_, 0))
  }

  private def methodForRedirect[F[_]](req: Request[F], resp: Response[F]): Option[Method] =
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
            Some(GET)

          case m =>
            Some(m)
        }

      case 303 =>
        // "303 (See Other) status code indicates that the server is
        // redirecting the user agent to a different resource, as indicated
        // by a URI in the Location header field, which is intended to
        // provide an indirect response to the original request.  A user
        // agent can perform a retrieval request targeting that URI (a GET
        // or HEAD request if using HTTP)" -- RFC 7231
        req.method match {
          case HEAD => Some(HEAD)
          case _ => Some(GET)
        }

      case 307 | 308 =>
        // "Note: This status code is similar to 302 (Found), except that
        // it does not allow changing the request method from POST to GET.
        // This specification defines no equivalent counterpart for 301
        // (Moved Permanently) ([RFC7238], however, defines the status code
        // 308 (Permanent Redirect) for this purpose). These status codes
        // may not change the method." -- RFC 7231
        //
        Some(req.method)

      case _ =>
        None
    }

  private val redirectUrisKey = Key.newKey[IO, List[Uri]].unsafeRunSync

  /**
    * Get the redirection URIs for a `response`.
    * Excludes the initial request URI
    */
  def getRedirectUris[F[_]](response: Response[F]): List[Uri] =
    response.attributes.lookup(redirectUrisKey).getOrElse(Nil)
}
