package org.http4s

import org.http4s.server.HttpService

object HttpService {
  val empty: HttpService = PartialFunction.empty
}
