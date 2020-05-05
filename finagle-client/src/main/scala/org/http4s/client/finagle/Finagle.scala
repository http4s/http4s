package org.http4s
package client
package finagle

import cats.effect._
import cats.syntax.functor._
import com.twitter.finagle.{Http,Service}
import com.twitter.finagle.http.{Request => Req, Response=>Resp, Method, RequestBuilder}
import com.twitter.util.{Return, Throw, Future}
import com.twitter.io._
import cats.syntax.flatMap._
import fs2.{
  Chunk, Stream
}

object Finagle {

  def allocate[F[_]](svc: Service[Req, Resp])(
      implicit F: ConcurrentEffect[F]): F[Client[F]] =
       F.delay(Client[F] { req =>
         Resource.liftF(for{
           freq <- toFinagleReq(req)
           resp <- toF(svc(freq))
         }yield resp).map(toHttp4sResp)
        })

  def resource[F[_]](dest: String)(
    implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] = mkResource(Http.newService(dest))

  def mkResource[F[_]](svc: Service[Req, Resp])(
    implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] = {
    Resource.make(F.delay(svc)){_ => F.delay(())}
    .flatMap(svc => Resource.liftF(allocate(svc)))
  }
  def toFinagleReq[F[_]](req: Request[F])(implicit F: ConcurrentEffect[F]):F[Req] = {
    val method = Method(req.method.name)
    val reqheaders = req.headers.toList.map(h => (h.name.toString, h.value)).toMap
    val reqBuilder = RequestBuilder().url(req.uri.toString)
    .addHeaders(reqheaders)
    (method, req.headers) match {
      case (Method.Get, _) =>
        F.delay(reqBuilder.buildGet)
      case (method, _) if req.isChunked =>
        val request = reqBuilder.build(method, None)
        request.headerMap.remove("Transfer-Encoding")
        val writer = request.chunkWriter
        request.setChunked(true)
        val bodyUpdate = req.body.chunks.map(_.toArray).evalMap{ a=>
          val out = (writer.write(com.twitter.finagle.http.Chunk.fromByteArray(a)))
          toF(out)
        }.compile.drain.map(_ => toF((writer.close())))
        ConcurrentEffect[F].runCancelable(bodyUpdate)(_=>IO.unit).to[F].as(request)
      case (method, _) =>
        req.as[Array[Byte]].map{b=>
          reqBuilder.build(method, Some(Buf.ByteArray.Owned(b)))
        }
    }
  }

  def toHttp4sResp[F[_]](resp: Resp): Response[F] = {
    def toStream(buf: Buf): Stream[F, Byte] = {
      Stream.chunk[F, Byte](Chunk.array(Buf.ByteArray.Owned.extract(buf)))
    }
    val response = Response[F](
      status = Status(resp.status.code)
    ).withHeaders(Headers(resp.headerMap.toList.map{case (name, value) => Header(name, value)}))
      .withEntity(toStream(resp.content))
    response
  }

  def toF[F[_], A](f: Future[A])(implicit F: Async[F]): F[A] = F.async{cb=>
    f.respond{
      case Return(value) => cb(Right(value))
      case Throw(exception) => cb(Left(exception))
    }
    ()
  }
}
