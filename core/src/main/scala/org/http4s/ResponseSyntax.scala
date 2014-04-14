package org.http4s

import scalaz.concurrent.Task
import org.http4s.Header.{`Set-Cookie`, `Content-Type`}
import org.joda.time.DateTime
import org.http4s.util.jodaTime.UnixEpoch

/**
 * Created by Bryce Anderson on 3/25/14.
 */

object ResponseSyntax extends ResponseSyntax


trait ResponseSyntax {

  implicit def addHelpers(r: Task[Response]) = new ResponseSyntaxBase[Task[Response]] {
    override protected def translateResponse(f: Response => Response) = r.map(f)
  }

  trait ResponseSyntaxBase[T] {

    protected def translateResponse(f: Response => Response): T

    /** Extension methods */

    /** Added the [[`Content-Type`]] header to the response */
    def withType(t: MediaType): T = translateResponse{ r =>
      r.copy(headers = r.headers :+ `Content-Type`(t))
    }

    /** Add a Set-Cookie header for the provided [[Cookie]] */
    def addCookie(cookie: Cookie): T = translateResponse(_.addHeader(Header.Cookie(cookie)))

    /** Add a Set-Cookie header with the provided values */
    def addCookie(name: String,
                  content: String,
                  expires: Option[DateTime] = None): T = addCookie(Cookie(name, content, expires))

    /** Replace the existing headers with those provided */
    def withHeaders(headers: Headers): T = translateResponse(_.copy(headers = headers))

    /** Replace the existing headers with those provided */
    def withHeaders(headers: Header*): T = withHeaders(Headers(headers.toList))

    /** Add the provided headers to the existing headers */
    def addHeaders(headers: Headers): T = translateResponse(r => r.copy(headers = r.headers ++ headers))

    /** Add the provided headers to the existing headers */
    def addHeaders(headers: Header*): T = translateResponse(r => r.copy(headers = r.headers ++ headers))

    /** Add the provided headers to the existing headers, replacing those of the same header name */
    def putHeaders(headers: Headers): T = putHeaders(headers: _*)

    /** Add the provided headers to the existing headers, replacing those of the same header name */
    def putHeaders(headers: Header*): T = {
      if (headers.isEmpty) translateResponse(identity)
      else translateResponse(r => r.copy(headers = r.headers.put(headers.head, headers.tail:_*)))
    }

    /** Add a [[`Set-Cookie`]] which will remove the specified cookie from the client */
    def removeCookie(cookie: Cookie): T =
      translateResponse(_.addHeader(`Set-Cookie`(cookie.copy(content = "",
        expires = Some(UnixEpoch), maxAge = Some(0)))))

    /** Add a Set-Cookie which will remove the specified cookie from the client */
    def removeCookie(name: String): T = translateResponse(_.addHeader(Header.`Set-Cookie`(
      Cookie(name, "", expires = Some(UnixEpoch), maxAge = Some(0))
    )))


    def addAttribute[V](entry: AttributeEntry[V]): T = addAttribute(entry.key, entry.value)

    def addAttribute[V](key: AttributeKey[V], value: V): T = translateResponse { r =>
      r.copy(attributes = r.attributes.put(key, value))
    }

    def withStatus[T <% Status](status: T) = translateResponse(_.copy(status = status))
  }

}

