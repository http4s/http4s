package org.http4s.grizzly

import play.api.libs.iteratee._
import concurrent.{ExecutionContext, Promise, Future}
import org.glassfish.grizzly.http.server.io.NIOInputStream
import org.glassfish.grizzly.ReadHandler
import org.http4s._

/**
 * @author Bryce Anderson
 *
 */
class BodyEnumerator(is: NIOInputStream, chunkSize:Int = 32 * 1024)(implicit ctx: ExecutionContext) extends Enumerator[Raw] {
  def apply[A](i: Iteratee[org.http4s.Raw, A]): Future[Iteratee[org.http4s.Raw, A]] = {

    i.fold {
      case Step.Cont(f) => {
        val promise = Promise[Iteratee[_root_.org.http4s.Raw, A]]
        is.notifyAvailable(new ReadHandler {
          var currentContinuation = f

          def onError(t: Throwable) {
            promise.failure(t)
            sys.error(s"Error in ReadHandler of Grizzly custom Enumerator: $t")
          }

          def onAllDataRead() {
            val bytes = new Raw(is.readyData())
            val readBytes = is.read(bytes,0,bytes.length)
            val newItter = f(Input.El(bytes.take(readBytes)))

            promise.completeWith(Enumerator.eof |>> newItter)
          }

          def onDataAvailable() {
            val bytes = new Raw(is.readyData())
            val readBytes = is.read(bytes,0,bytes.length)
            val newItter = f(Input.El(bytes.take(readBytes)))

            newItter.pureFold{
                case Step.Cont(f) =>
                  currentContinuation = f
                  is.notifyAvailable(this)

                case Step.Done(a,e) => promise.success(Done(a,e))
                case Step.Error(msg,e) => sys.error(s"Iteratee returned an error: $e")
            }
          }
        }, chunkSize)
        promise.future
      } // case Step.Cont

      case Step.Done(result,remaining) => Future(Done(result,remaining))
      case Step.Error(msg, remaining) => sys.error(msg)
    }
  }
}
