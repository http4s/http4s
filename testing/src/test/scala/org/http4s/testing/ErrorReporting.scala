/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/typelevel/cats-effect/blob/v1.0.0/core/shared/src/test/scala/cats/effect/internals/TestUtils.scala
 * Copyright (c) 2017-2018 The Typelevel Cats-effect Project Developers
 */

package org.http4s.testing
import java.io.{ByteArrayOutputStream, PrintStream}
import cats.implicits._
import cats.Monad
import org.http4s.{Headers, MessageFailure, Request, Response, Status}
import org.http4s.headers.{Connection, `Content-Length`}
import org.typelevel.ci.CIString
import scala.util.control.NonFatal
import java.net.URL
import java.security.{
  AccessControlContext,
  CodeSource,
  Permissions,
  PrivilegedAction,
  ProtectionDomain
}

object ErrorReporting {

  /**
    * Silences System.out and System.err streams for the duration of thunk.
    * Restores the original streams before exiting.
    */
  def silenceOutputStreams[R](thunk: => R): R =
    synchronized {
      val originalOut = System.out
      val originalErr = System.err

      // Create blank permissions
      val perm = new Permissions

      // CodeSource domain for which these permissions apply (all files)
      val certs: Array[java.security.cert.Certificate] = null
      val code: CodeSource = new CodeSource(new URL("file:/*"), certs)

      val domain = new ProtectionDomain(code, perm)
      val domains = new Array[ProtectionDomain](1)
      domains(0) = domain
      val context = new AccessControlContext(domains)

      // Redirect output to dummy stream
      val fakeOutStream = new PrintStream(NullOutStream)
      val fakeErrStream = new PrintStream(NullOutStream)
      System.setOut(fakeOutStream)
      System.setErr(fakeErrStream)
      try java.security.AccessController.doPrivileged(
        new PrivilegedAction[R]() {
          override def run: R = thunk
        },
        context)
      finally {
        // Set back the original streams
        System.setOut(originalOut)
        System.setErr(originalErr)
      }
    }

  /**
    * Returns an ErrorHandler that does not log
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
              Connection(CIString("close")) ::
                `Content-Length`.zero ::
                Nil
            )))
    }

  /**
    * Silences `System.err`, only printing the output in case exceptions are
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

  /**
    * Catches `System.err` output, for testing purposes.
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
