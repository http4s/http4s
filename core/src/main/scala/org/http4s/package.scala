package org

import play.api.libs.iteratee.Iteratee

package object http4s {
  type Action = Iteratee[Array[Byte], Response]
  type Route = PartialFunction[Request, Action]
}