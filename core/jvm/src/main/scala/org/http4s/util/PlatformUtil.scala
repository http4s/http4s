package org.http4s
package util

trait PlatformUtil {
  @deprecated("Use fs2.StreamApp instead", "0.18.0-M6")
  type StreamApp[F[_]] = fs2.StreamApp[F]

  @deprecated("Use fs2.StreamApp.ExitCode instead", "0.18.0-M6")
  type ExitCode = fs2.StreamApp.ExitCode
  @deprecated("Use fs2.StreamApp.ExitCode instead", "0.18.0-M6")
  val ExitCode = fs2.StreamApp.ExitCode
}
