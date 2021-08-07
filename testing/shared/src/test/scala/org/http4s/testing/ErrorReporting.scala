/*
 * Copyright 2016 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/typelevel/cats-effect/blob/v1.0.0/core/shared/src/test/scala/cats/effect/internals/TestUtils.scala
 * Copyright (c) 2017-2018 The Typelevel Cats-effect Project Developers
 */

package org.http4s
package testing

import java.io.{ByteArrayOutputStream, PrintStream}
import cats.syntax.all._
import cats.Monad
import org.http4s.headers.{Connection, `Content-Length`}
import org.typelevel.ci._
import scala.util.control.NonFatal

object ErrorReporting extends ErrorReportingPlatform {

  /** Returns an ErrorHandler that does not log
    */
  def silentErrorHandler[F[_], G[_]](implicit
      F: Monad[F]): Request[F] => PartialFunction[Throwable, F[Response[G]]] =
    req => {
      case mf: MessageFailure =>
        mf.toHttpResponse[G](req.httpVersion).pure[F]
      case NonFatal(_) =>
        F.pure(
          Response(
            Status.InternalServerError,
            req.httpVersion,
            Headers(
              Connection(ci"close"),
              `Content-Length`.zero
            )))
    }

  /** Silences `System.err`, only printing the output in case exceptions are
    * thrown by the executed `thunk`.
    */
  def silenceSystemErr[A](thunk: => A): A =
    synchronized {
      // Silencing System.err
      val oldErr = System.err
      val outStream = new ByteArrayOutputStream()
      val fakeErr = new PrintStream(outStream)
      System.setErr(fakeErr)
      try {
        val result = thunk
        System.setErr(oldErr)
        result
      } catch {
        case NonFatal(e) =>
          System.setErr(oldErr)
          // In case of errors, print whatever was caught
          fakeErr.close()
          val out = outStream.toString("utf-8")
          if (out.nonEmpty) oldErr.println(out)
          throw e
      }
    }

  /** Catches `System.err` output, for testing purposes.
    */
  def catchSystemErr(thunk: => Unit): String =
    synchronized {
      val oldErr = System.err
      val outStream = new ByteArrayOutputStream()
      val fakeErr = new PrintStream(outStream)
      System.setErr(fakeErr)
      try thunk
      finally {
        System.setErr(oldErr)
        fakeErr.close()
      }
      outStream.toString("utf-8")
    }
}
