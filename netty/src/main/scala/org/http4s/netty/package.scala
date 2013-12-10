package org.http4s

import io.netty.channel.{ChannelFutureListener, ChannelFuture, Channel}
import io.netty.handler.codec.{http => n}
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}
import org.http4s.ServerProtocol.HttpVersion
import scala.concurrent.{CanAwait, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.Duration
import scala.util.control.NoStackTrace

package object netty {

  case object Cancelled extends Throwable with NoStackTrace
  case class ChannelError(val channel: Channel, val reason: Throwable) extends Throwable


  implicit class ImplChannelFuture(val cf: ChannelFuture) extends AnyRef with Future[Channel] {

    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
      cf.await(atMost.toMillis)
      this
    }

    def result(atMost: Duration)(implicit permit: CanAwait): Channel = {
      ready(atMost).cf.channel()
    }

    def onComplete[U](func: (Try[Channel]) => U)(implicit executor: ExecutionContext): Unit = {
      cf.addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          if (future.isSuccess)         func(Success(future.channel()))
          else if (future.isCancelled)  func(Failure(Cancelled))
          else                          func(Failure(future.cause()))
        }
      })
    }

    def isCompleted: Boolean = cf.isDone

    def value: Option[Try[Channel]] = {
      if (isCompleted) {
        if (cf.isSuccess) Some(Success(cf.channel()))
        else Some(Failure(cf.cause()))
      }
      else if (cf.isCancelled) Some(Failure(Cancelled))
      else None
    }
  }

  implicit class TaskSyntax[+A](t: Task[A]) {
    def handleWith[B>:A](f: PartialFunction[Throwable,Task[B]]): Task[B] = {
      t.attempt.flatMap {
        case -\/(t) => f.applyOrElse(t, Task.fail)
        case \/-(r) => Task.now(r)
      }
    }
  }

  private[netty] implicit def channelFuture2Task(cf: ChannelFuture): Task[Channel] = {
    if(cf.isDone) Task.now(cf.channel())
    else Task.async{ cb =>
      if(cf.isDone) {
        if (cf.isSuccess) cb(\/-(cf.channel()))
        else if (cf.isCancelled) cb(-\/((Cancelled)))
        else cb(-\/((cf.cause)))
      }
      else cf.addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          if (future.isSuccess) cb(\/-((future.channel)))
          else if (future.isCancelled) cb(-\/((Cancelled)))
          else cb(-\/((future.cause)))
        }
      })
    }
  }

  implicit def jHttpMethod2HttpMethod(orig: n.HttpMethod): Method = orig match {
    case n.HttpMethod.CONNECT => Method.Connect
    case n.HttpMethod.DELETE => Method.Delete
    case n.HttpMethod.GET => Method.Get
    case n.HttpMethod.HEAD => Method.Head
    case n.HttpMethod.OPTIONS => Method.Options
    case n.HttpMethod.PATCH => Method.Patch
    case n.HttpMethod.POST => Method.Post
    case n.HttpMethod.PUT => Method.Put
    case n.HttpMethod.TRACE => Method.Trace
  }

  implicit def respStatus2nettyStatus(stat: Status) = new n.HttpResponseStatus(stat.code, stat.reason.blankOption.getOrElse(""))
  implicit def respStatus2nettyStatus(stat: n.HttpResponseStatus) = Status(stat.code, stat.reasonPhrase)
  implicit def httpVersion2nettyVersion(ver: HttpVersion) = ver match {
    case HttpVersion(1, 1) => n.HttpVersion.HTTP_1_1
    case HttpVersion(1, 0) => n.HttpVersion.HTTP_1_0
  }
}
