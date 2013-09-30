package org.http4s

import io.netty.channel.{ChannelFutureListener, ChannelFuture, Channel}
import concurrent.{Promise, Future}
import io.netty.handler.codec.http

package object netty {

  class Cancelled(val channel: Channel) extends Throwable
  class ChannelError(val channel: Channel, val reason: Throwable) extends Throwable

  private[netty] implicit def channelFuture2Future(cf: ChannelFuture): Future[Channel] = {
    val prom = Promise[Channel]()
    cf.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) {
        if (future.isSuccess) {
          prom.success(future.channel)
        } else if (future.isCancelled) {
          prom.failure(new Cancelled(future.channel))
        } else {
          prom.failure(new ChannelError(future.channel, future.cause))
        }
      }
    })
    prom.future
  }

  implicit def jHttpMethod2HttpMethod(orig: http.HttpMethod): Method = orig match {
    case http.HttpMethod.CONNECT => Connect
    case http.HttpMethod.DELETE => Delete
    case http.HttpMethod.GET => Get
    case http.HttpMethod.HEAD => Head
    case http.HttpMethod.OPTIONS => Options
    case http.HttpMethod.PATCH => Patch
    case http.HttpMethod.POST => Post
    case http.HttpMethod.PUT => Put
    case http.HttpMethod.TRACE => Trace
  }

  implicit def respStatus2nettyStatus(stat: Status) = new http.HttpResponseStatus(stat.code, stat.reason.blankOption.getOrElse(""))
  implicit def respStatus2nettyStatus(stat: http.HttpResponseStatus) = Status(stat.code, stat.reasonPhrase)
  implicit def httpVersion2nettyVersion(ver: HttpVersion) = ver match {
    case HttpVersion(1, 1) => http.HttpVersion.HTTP_1_1
    case HttpVersion(1, 0) => http.HttpVersion.HTTP_1_0
  }
}
