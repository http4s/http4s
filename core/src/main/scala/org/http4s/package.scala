package org

import play.api.libs.iteratee.Iteratee

package object http4s {
  type Handler = Iteratee[Array[Byte], Result]
  type Route = PartialFunction[Request, Handler]
}