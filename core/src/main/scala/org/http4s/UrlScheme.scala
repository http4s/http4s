package org.http4s

trait UrlScheme // IPC: added a non-sealed base trait because there are other schemes out there
sealed trait HttpUrlScheme extends UrlScheme

object HttpUrlScheme {
  case object Http extends HttpUrlScheme
  case object Https extends HttpUrlScheme

  def apply(name: String): HttpUrlScheme = name match {
    case "http" => Http
    case "https" => Https
  }
}