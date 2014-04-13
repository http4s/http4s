package org.http4s

import scalaz.concurrent.Task
import org.http4s.Header.`Content-Type`
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

    def withType(t: MediaType): T = translateResponse{ r =>
      r.copy(headers = r.headers :+ `Content-Type`(t))
    }

    def addCookie(cookie: Cookie): T = translateResponse(_.addHeader(Header.Cookie(cookie)))

    def addCookie(name: String,
                  content: String,
                  expires: Option[DateTime] = None): T = addCookie(Cookie(name, content, expires))

    def withHeaders(headers: Headers): T = translateResponse(_.copy(headers = headers))

    def withHeaders(headers: Header*): T = withHeaders(Headers(headers.toList))

    def addHeaders(headers: Headers): T = translateResponse(r => r.copy(headers = r.headers ++ headers))

    def addHeaders(headers: Header*): T = translateResponse(r => r.copy(headers = r.headers ++ headers))

    def removeCookie(cookie: Cookie): T =
      translateResponse(_.addHeader(Header.`Set-Cookie`(cookie.copy(content = "",
        expires = Some(UnixEpoch), maxAge = Some(0)))))

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

