/*
package org.http4s.netty

<<<<<<< HEAD
import org.http4s.Chunk
=======
import play.api.libs.iteratee._
import org.http4s.Chunk
>>>>>>> develop
import scala.concurrent.{ExecutionContext, Promise, Future}
import scala.util.{Failure, Success}

/**
 * @author Bryce Anderson
 *         Created on 9/1/13
 */


class ChunkEnum(implicit ec: ExecutionContext) extends Enumerator[Chunk] {
  private var i: Future[Iteratee[Chunk, _]] = null
  private val p = Promise[Iteratee[Chunk, _]]

  def apply[A](i: Iteratee[Chunk, A]): Future[Iteratee[Chunk, A]] = {
    this.i = Future.successful(i)
    p.future.asInstanceOf[Future[Iteratee[Chunk, A]]]
  }

  def push(chunk: Chunk) {
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
}*/
