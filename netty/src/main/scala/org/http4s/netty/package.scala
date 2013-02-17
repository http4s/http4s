package org.http4s

import org.jboss.netty.channel.{ChannelFutureListener, ChannelFuture, Channel}
import concurrent.{ExecutionContext, Promise, Future}
import scala.util.{Failure, Success}
import scala.language.implicitConversions
import org.jboss.netty.handler.codec.http.{ HttpMethod => JHttpMethod, HttpResponseStatus }
import Method._

package object netty {

  class Cancelled(val channel: Channel) extends Throwable
  class ChannelError(val channel: Channel, val reason: Throwable) extends Throwable

  private[netty] implicit def channelFuture2Future(cf: ChannelFuture)(implicit executionContext: ExecutionContext): Future[Channel] = {
    val prom = Promise[Channel]()
    cf.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) {
        if (future.isSuccess) {
          prom.complete(Success(future.getChannel))
        } else if (future.isCancelled) {
          prom.complete(Failure(new Cancelled(future.getChannel)))
        } else {
          prom.complete(Failure(new ChannelError(future.getChannel, future.getCause)))
        }
      }
    })
    prom.future
  }

  implicit def jHttpMethod2HttpMethod(orig: JHttpMethod): Method = orig match {
    case JHttpMethod.CONNECT => Connect
    case JHttpMethod.DELETE => Delete
    case JHttpMethod.GET => Get
    case JHttpMethod.HEAD => Head
    case JHttpMethod.OPTIONS => Options
    case JHttpMethod.PATCH => Patch
    case JHttpMethod.POST => Post
    case JHttpMethod.PUT => Put
    case JHttpMethod.TRACE => Trace
  }

  implicit def respStatus2nettyStatus(stat: Status) = new HttpResponseStatus(stat.code, stat.reason.blankOption.getOrElse(""))
  implicit def respStatus2nettyStatus(stat: HttpResponseStatus) = Status(stat.getCode, stat.getReasonPhrase)
  implicit def httpVersion2nettyVersion(ver: HttpVersion) = ver match {
    case HttpVersion(1, 1) => org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
    case HttpVersion(1, 0) => org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_0
  }
}
