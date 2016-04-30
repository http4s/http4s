package org.http4s
package client

import scala.collection.JavaConverters._
import org.asynchttpclient.HttpResponseStatus
import io.netty.handler.codec.http.HttpHeaders

package object asynchttpclient {
  protected[asynchttpclient] def getStatus(status: HttpResponseStatus): Status =
    Status.fromInt(status.getStatusCode).valueOr(throw _)

  protected[asynchttpclient] def getHeaders(headers: HttpHeaders): Headers =
    Headers(headers.iterator.asScala.map { header =>
      Header(header.getKey, header.getValue)
    }.toList)
}
