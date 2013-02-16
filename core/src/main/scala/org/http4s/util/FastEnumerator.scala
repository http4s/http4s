package org.http4s.util

import play.api.libs.iteratee.{Enumerator, Iteratee, Step, Input}
import scala.concurrent.Future

/**
 * @author Bryce Anderson
 * Created on 2/16/13 at 9:42 AM
 */

/*
  The only point here is to make Enumerators in a more efficient way than the play Iteratee library does.
 */
object FastEnumerator {
  def apply[A](in: A*)(implicit ctx:scala.concurrent.ExecutionContext = concurrent.ExecutionContext.Implicits.global): Enumerator[A] = new Enumerator[A] {
    def apply[E](it: Iteratee[A, E]): Future[Iteratee[A, E]] = {
      in.foldLeft(Future.successful(it)){(i, e) =>
        i.flatMap(it => it.pureFold{
          case Step.Cont(k) => k(Input.El(e))
          case _ => it
        })
      }
    }
  }
}
