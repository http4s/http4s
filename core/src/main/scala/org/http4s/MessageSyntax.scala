package org.http4s

import scalaz.concurrent.Task
import org.http4s.Header.{`Set-Cookie`, `Content-Type`}

/**
 * Created by Bryce Anderson on 3/25/14.
 */

object MessageSyntax extends MessageSyntax

trait MessageSyntax {

  implicit def addHelpers(r: Task[Response]) = new ResponseSyntax[Task[Response]] {
    override protected def translateMessage(f: Response => Response) = r.map(f)

    override protected def translateWithTask(f: (Response) => Task[Response]): Task[Response] = r.flatMap(f)
  }

  implicit def addHelpers(r: Task[Request]) = new MessageSyntax[Task[Request], Request] {
    override protected def translateMessage(f: (Request) => Request): Task[Request] = r.map(f)

    override protected def translateWithTask(f: (Request) => Task[Request]): Task[Request] = r.flatMap(f)
  }

  trait MessageSyntax[T, M <: Message] {

    protected def translateMessage(f: M => M#Self): T
    
    protected def translateWithTask(f: M => Task[M#Self]): Task[M#Self]

    /** Replace the body of the incoming Request object
      *
      * @param body body of type T
      * @param w [[Writable]] corresponding to the body
      * @tparam B type of the body
      * @return new message
      */
    def withBody[B](body: B)(implicit w: Writable[B]): Task[M#Self] = withBody(body, w.contentType)

    def withBody[B](body: B, contentType: `Content-Type`)(implicit w: Writable[B]): Task[M#Self] = {
      translateWithTask { self =>
        w.toBody(body).map { case (proc, len) =>
          val h = len match {
            case Some(l) => self.headers.put(Header.`Content-Length`(l), contentType)
            case None => self.headers.put(contentType)
          }
          self.withBHA(body = proc, headers = h)
        }
      }
    }

    /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
      *
      * @param key [[AttributeKey]] with which to associate the value
      * @param value value associated with the key
      * @tparam A type of the value to store
      * @return a new message object with the key/value pair appended
      */
    def withAttribute[A](key: AttributeKey[A], value: A): T = translateMessage { r =>
      r.withBHA(attributes = r.attributes.put(key, value))
    }

    /** Added the [[`Content-Type`]] header to the response */
    def withType(t: MediaType): T = translateMessage{ r =>
      r.withBHA(headers = r.headers :+ `Content-Type`(t))
    }

    /** Replace the body of the incoming Request object
      *
      * @param body scalaz.stream.Process[Task,Chunk] representing the new body
      * @return a new Request object
      */
    def withBody(body: HttpBody): T = translateMessage{ r => r.withBHA(body = body) }

    def withContentType(contentType: Option[`Content-Type`]): T = translateMessage { r =>
      r.withBHA(headers = contentType match {
        case Some(ct) => r.headers.put(ct)
        case None => r.headers.filterNot(_.is(Header.`Content-Type`))
      })
    }

    def filterHeaders(f: Header => Boolean): T = translateMessage { r => r.withBHA(headers = r.headers.filter(f))}

    def removeHeader(key: HeaderKey): T = filterHeaders(_ isNot key)

    /** Replaces the [[Header]]s of the incoming Request object
      *
      * @param headers [[Headers]] containing the desired headers
      * @return a new Request object
      */
    def withHeaders(headers: Headers): T = translateMessage(_.withBHA(headers = headers))

    /** Replace the existing headers with those provided */
    def withHeaders(headers: Header*): T = withHeaders(Headers(headers.toList))

    /** Add the provided headers to the existing headers */
    def addHeaders(headers: Header*): T = translateMessage(r => r.withBHA(headers = r.headers ++ headers))

    /** Add the provided header to the existing headers */
    def addHeader(header: Header): T = translateMessage(r => r.withBHA(headers = header +: r.headers))

    /** Add the provided header to the existing headers, replacing those of the same header name */
    def putHeader(header: Header): T = translateMessage(r => r.withBHA(headers = r.headers.put(header)))

    /** Add the provided headers to the existing headers, replacing those of the same header name */
    def putHeaders(headers: Header*): T = translateMessage(r => r.withBHA(headers = r.headers.put(headers:_*)))

    def addAttribute[V](entry: AttributeEntry[V]): T = addAttribute(entry.key, entry.value)

    def addAttribute[V](key: AttributeKey[V], value: V): T = translateMessage { r =>
      r.withBHA(attributes = r.attributes.put(key, value))
    }

    def withTrailerHeaders(trailerHeaders: Task[Headers]): T = translateMessage{ r =>
      r.withBHA(attributes = r.attributes.put(Message.Keys.TrailerHeaders, trailerHeaders))
    }
  }


  trait ResponseSyntax[T] extends MessageSyntax[T, Response] {

    /** Response specific extension methods */

    /** Add a Set-Cookie header for the provided [[Cookie]] */
    def addCookie(cookie: Cookie): T = addHeaders(Header.`Set-Cookie`(cookie))

    /** Add a Set-Cookie header with the provided values */
    def addCookie(name: String,
                  content: String,
                  expires: Option[DateTime] = None): T = addCookie(Cookie(name, content, expires))

    /** Add a [[`Set-Cookie`]] which will remove the specified cookie from the client */
    def removeCookie(cookie: Cookie): T = addHeaders(`Set-Cookie`(cookie.copy(content = "",
        expires = Some(DateTime.UnixEpoch), maxAge = Some(0))))

    /** Add a Set-Cookie which will remove the specified cookie from the client */
    def removeCookie(name: String): T = addHeaders(Header.`Set-Cookie`(
      Cookie(name, "", expires = Some(DateTime.UnixEpoch), maxAge = Some(0))
    ))

    def withStatus[T <% Status](status: T) = translateMessage(_.copy(status = status))
  }

}

