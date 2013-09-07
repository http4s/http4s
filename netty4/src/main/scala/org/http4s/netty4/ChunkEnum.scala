package org.http4s.netty4

import play.api.libs.iteratee._
import org.http4s.HttpChunk
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.util.{Failure, Success}

/**
 * @author Bryce Anderson
 *         Created on 9/1/13
 */


class ChunkEnum(implicit ec: ExecutionContext) extends Enumerator[HttpChunk] {
  private var i: Future[Iteratee[HttpChunk, _]] = null
  private val p = Promise[Iteratee[HttpChunk, _]]

  def apply[A](i: Iteratee[HttpChunk, A]): Future[Iteratee[HttpChunk, A]] = {
    this.i = Future.successful(i)
    p.future.asInstanceOf[Future[Iteratee[HttpChunk, A]]]
  }

  def push(chunk: HttpChunk) {
    assert(i != null)
    i = i.flatMap(_.pureFold {
      case Step.Cont(f) =>
        f(Input.El(chunk))

        // Complete the future with these.
      case Step.Done(a, r) =>
        val i = Done(a, r)
        p.trySuccess(i)
        i

      case Step.Error(e, a) =>
        val i = Error(e, a)
        p.trySuccess(i)
        i
    })
  }

  def close() {
    assert(i != null)
    i.onComplete{
      case Success(it) => p.completeWith(it.feed(Input.EOF))
      case Failure(t) => sys.error("Failed to finish the set.")
    }
  }

  def abort(t: Throwable) {
    p.failure(t)
    i = null
  }
}