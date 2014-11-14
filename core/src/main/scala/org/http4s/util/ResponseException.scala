package org.http4s.util

import org.http4s._


trait ReplyException { self: Exception =>
  def asResponse(version: HttpVersion): Response
}
