package org.http4s

sealed trait UrlScheme

object UrlScheme {
  case object Http extends UrlScheme
  case object Https extends UrlScheme

  def apply(name: String) = name match {
    case "http" => Http
    case "https" => Https
  }
}
