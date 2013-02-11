package org.http4s

import util.DateTime


// see http://tools.ietf.org/html/rfc6265
case class HttpCookie(
  name: String,
  content: String,
  expires: Option[DateTime] = None,
  maxAge: Option[Long] = None,
  domain: Option[String] = None,
  path: Option[String] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  extension: Option[String] = None
) {
  def value: String = name + "=\"" + content + '"' +
                      expires.map("; Expires=" + _.toRfc1123DateTimeString).getOrElse("") +
                      maxAge.map("; Max-Age=" + _).getOrElse("") +
                      domain.map("; Domain=" + _).getOrElse("") +
                      path.map("; Path=" + _).getOrElse("") +
                      (if (secure) "; Secure" else "") +
                      (if (httpOnly) "; HttpOnly" else "") +
                      extension.map("; " + _).getOrElse("")

  override def toString = value
}
