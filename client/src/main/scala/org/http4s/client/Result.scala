package org.http4s.client

import org.http4s.{Headers, Status, Response}

/** A decoded [[Response]]
  *
  * @param status [[Status]] of the [[Response]]
  * @param headers [[Headers]] of the [[Response]]
  * @param body the body of the [[Response]] decoded to type T
  * @tparam T type of the decoded body
  */
case class Result[T](status: Status, headers: Headers, body: T)