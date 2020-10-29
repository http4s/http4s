/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.servlet

import javax.servlet.ServletInputStream
import javax.servlet.http.HttpServletRequest

case class HttpServletRequestStub(
    inputStream: ServletInputStream
) extends HttpServletRequest {
  def getInputStream(): ServletInputStream = inputStream

  def authenticate(x$1: javax.servlet.http.HttpServletResponse): Boolean = ???
  def changeSessionId(): String = ???
  def getAuthType(): String = ???
  def getContextPath(): String = ???
  def getCookies(): Array[javax.servlet.http.Cookie] = ???
  def getDateHeader(x$1: String): Long = ???
  def getHeader(x$1: String): String = ???
  def getHeaderNames(): java.util.Enumeration[String] = ???
  def getHeaders(x$1: String): java.util.Enumeration[String] = ???
  def getIntHeader(x$1: String): Int = ???
  def getMethod(): String = ???
  def getPart(x$1: String): javax.servlet.http.Part = ???
  def getParts(): java.util.Collection[javax.servlet.http.Part] = ???
  def getPathInfo(): String = ???
  def getPathTranslated(): String = ???
  def getQueryString(): String = ???
  def getRemoteUser(): String = ???
  def getRequestURI(): String = ???
  def getRequestURL(): StringBuffer = ???
  def getRequestedSessionId(): String = ???
  def getServletPath(): String = ???
  def getSession(): javax.servlet.http.HttpSession = ???
  def getSession(x$1: Boolean): javax.servlet.http.HttpSession = ???
  def getUserPrincipal(): java.security.Principal = ???
  def isRequestedSessionIdFromCookie(): Boolean = ???
  def isRequestedSessionIdFromURL(): Boolean = ???
  def isRequestedSessionIdFromUrl(): Boolean = ???
  def isRequestedSessionIdValid(): Boolean = ???
  def isUserInRole(x$1: String): Boolean = ???
  def login(x$1: String, x$2: String): Unit = ???
  def logout(): Unit = ???
  def upgrade[T <: javax.servlet.http.HttpUpgradeHandler](x$1: Class[T]): T = ???
  def getAsyncContext(): javax.servlet.AsyncContext = ???
  def getAttribute(x$1: String): Object = ???
  def getAttributeNames(): java.util.Enumeration[String] = ???
  def getCharacterEncoding(): String = ???
  def getContentLength(): Int = ???
  def getContentLengthLong(): Long = ???
  def getContentType(): String = ???
  def getDispatcherType(): javax.servlet.DispatcherType = ???
  def getLocalAddr(): String = ???
  def getLocalName(): String = ???
  def getLocalPort(): Int = ???
  def getLocale(): java.util.Locale = ???
  def getLocales(): java.util.Enumeration[java.util.Locale] = ???
  def getParameter(x$1: String): String = ???
  def getParameterMap(): java.util.Map[String, Array[String]] = ???
  def getParameterNames(): java.util.Enumeration[String] = ???
  def getParameterValues(x$1: String): Array[String] = ???
  def getProtocol(): String = ???
  def getReader(): java.io.BufferedReader = ???
  def getRealPath(x$1: String): String = ???
  def getRemoteAddr(): String = ???
  def getRemoteHost(): String = ???
  def getRemotePort(): Int = ???
  def getRequestDispatcher(x$1: String): javax.servlet.RequestDispatcher = ???
  def getScheme(): String = ???
  def getServerName(): String = ???
  def getServerPort(): Int = ???
  def getServletContext(): javax.servlet.ServletContext = ???
  def isAsyncStarted(): Boolean = ???
  def isAsyncSupported(): Boolean = ???
  def isSecure(): Boolean = ???
  def removeAttribute(x$1: String): Unit = ???
  def setAttribute(x$1: String, x$2: Object): Unit = ???
  def setCharacterEncoding(x$1: String): Unit = ???
  def startAsync(
      x$1: javax.servlet.ServletRequest,
      x$2: javax.servlet.ServletResponse): javax.servlet.AsyncContext = ???
  def startAsync(): javax.servlet.AsyncContext = ???
}
