package org.http4s.client.blaze

import org.http4s.Request
import org.http4s.Uri.{Authority, Scheme}
import org.http4s.util.string._

/**
  * Created by ross on 12/8/15.
  */
case class RequestKey(scheme: Scheme, authority: Authority)

object RequestKey {
  def fromRequest(request: Request): RequestKey = {
    val uri = request.uri
    RequestKey(uri.scheme.getOrElse("http".ci), uri.authority.getOrElse(Authority()))
  }
}
