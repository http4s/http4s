package org.http4s

import scalaz.concurrent.Task
import org.http4s.Header.`Content-Type`
import org.joda.time.DateTime

/**
 * Created by brycea on 3/25/14.
 */

object ResponseSyntax extends ResponseSyntax


trait ResponseSyntax {

  implicit def addHelpers(r: Task[Response]) = new ResponseSyntaxBase[Task[Response]] {
    override protected def map(f: Response => Response) = r.map(f)
  }

  implicit def addHelpers(r: Response) = new ResponseSyntaxBase[Response] {
    override protected def map(f: Response => Response): Response = f(r)
  }


  trait ResponseSyntaxBase[T] {

    protected def map(f: Response => Response): T

    /** Extension methods */

    def withType(t: MediaType): T = map{ r =>
      r.copy(headers = r.headers :+ `Content-Type`(t))
    }

    def addCookie(cookie: Cookie): T = map(_.addHeader(Header.Cookie(cookie)))

    def addCookie(name: String,
                  content: String,
                  expires: Option[DateTime] = None): T = addCookie(Cookie(name, content, expires))

    def withHeaders(headers: HeaderCollection): T = map(_.copy(headers = headers))

    def removeCookie(cookie: Cookie): T =
      map(_.addHeader(Header.`Set-Cookie`(cookie.copy(content = "", expires = Some(UnixEpoch), maxAge = Some(0)))))

    def removeCookie(name: String): T = map(_.addHeader(Header.`Set-Cookie`(
      Cookie(name, "", expires = Some(UnixEpoch), maxAge = Some(0))
    )))

    def withStatus[T <% Status](status: T) = map(_.copy(status = status))
  }

}

