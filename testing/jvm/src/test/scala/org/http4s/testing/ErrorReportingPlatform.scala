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

package org.http4s.testing

import java.io.PrintStream
import java.net.URL
import java.security.{
  AccessControlContext,
  CodeSource,
  Permissions,
  PrivilegedAction,
  ProtectionDomain
}

trait ErrorReportingPlatform {

  /** Silences System.out and System.err streams for the duration of thunk.
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

}
