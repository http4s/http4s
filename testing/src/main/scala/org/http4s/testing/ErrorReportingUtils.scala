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

import scala.util.control.NonFatal

@deprecated("Not related to HTTP, and will be removed from public interface.", "0.21.0-RC2")
trait ErrorReportingUtils {

  /**
    * Silences `System.err`, only printing the output in case exceptions are
    * thrown by the executed `thunk`.
    */
  def silenceSystemErr[A](thunk: => A): A = synchronized {
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
  def catchSystemErr(thunk: => Unit): String = synchronized {
    val oldErr = System.err
    val outStream = new ByteArrayOutputStream()
    val fakeErr = new PrintStream(outStream)
    System.setErr(fakeErr)
    try {
      thunk
    } finally {
      System.setErr(oldErr)
      fakeErr.close()
    }
    outStream.toString("utf-8")
  }
}
