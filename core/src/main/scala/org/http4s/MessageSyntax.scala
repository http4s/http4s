package org.http4s

import scalaz.concurrent.Task

object MessageSyntax extends MessageSyntax

trait MessageSyntax {

  implicit def requestSyntax(req: Task[Request]): TaskMessageMethods[Request] = new TaskMessageMethods[Request] {
    override def r = req
  }

  implicit def responseSyntax(resp: Task[Response]): TaskResponseMethods = new TaskResponseMethods {
    override def r = resp
  }

  /////////////// instance traits ///////////////////////////////////////////////

  trait TaskMessageMethods[M <: Message] extends MessageMethods {
    type Self = Task[M#Self]

    def r: Task[M]

    /** Add a body to the message
      * @see [[Message]]
      */
    def withBody[T](b: T)(implicit w: Writable[T]): Self = r.flatMap(_.withBody(b)(w))

    /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
      *
      * @param key [[AttributeKey]] with which to associate the value
      * @param value value associated with the key
      * @tparam A type of the value to store
      * @return a new message object with the key/value pair appended
      */
    override def withAttribute[A](key: AttributeKey[A], value: A): Self = r.map(_.withAttribute(key, value))

    /** Replaces the [[Header]]s of the incoming Request object
      *
      * @param headers [[Headers]] containing the desired headers
      * @return a new Request object
      */
    override def withHeaders(headers: Headers): Self = r.map(_.withHeaders(headers))

    /** Add the provided headers to the existing headers, replacing those of the same header name */
    override def putHeaders(headers: Header*): Self = r.map(_.putHeaders(headers:_*))

    override def filterHeaders(f: (Header) => Boolean): Self = r.map(_.filterHeaders(f))

    /** Add the provided headers to the existing headers */
    override def addHeaders(headers: Header*): Self = r.map(_.addHeaders(headers:_*))
  }

  trait TaskResponseMethods extends TaskMessageMethods[Response] with ResponseMethods {

    /** Response specific extension methods */
    override def withStatus[S <% Status](status: S): Self = r.map(_.withStatus(status))
  }
}
