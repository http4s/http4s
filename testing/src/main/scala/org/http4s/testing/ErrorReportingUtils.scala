package org.http4s.testing
import java.io.{ByteArrayOutputStream, PrintStream}

import scala.util.control.NonFatal

trait ErrorReportingUtils {

  /**
    * Silences `System.err`, only printing the output in case exceptions are
    * thrown by the executed `thunk`.
    * Credit: Typelevel - Cats Effect developers
    *
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
    * Credit: Typelevel - Cats Effect developers
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
