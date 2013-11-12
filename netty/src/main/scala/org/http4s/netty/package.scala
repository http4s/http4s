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
    case http.HttpMethod.CONNECT => Method.Connect
    case http.HttpMethod.DELETE => Method.Delete
    case http.HttpMethod.GET => Method.Get
    case http.HttpMethod.HEAD => Method.Head
    case http.HttpMethod.OPTIONS => Method.Options
    case http.HttpMethod.PATCH => Method.Patch
    case http.HttpMethod.POST => Method.Post
    case http.HttpMethod.PUT => Method.Put
    case http.HttpMethod.TRACE => Method.Trace
  }

  implicit def respStatus2nettyStatus(stat: Status) = new http.HttpResponseStatus(stat.code, stat.reason.blankOption.getOrElse(""))
  implicit def respStatus2nettyStatus(stat: http.HttpResponseStatus) = Status(stat.code, stat.reasonPhrase)
  implicit def httpVersion2nettyVersion(ver: HttpVersion) = ver match {
    case HttpVersion(1, 1) => http.HttpVersion.HTTP_1_1
    case HttpVersion(1, 0) => http.HttpVersion.HTTP_1_0
  }
}
