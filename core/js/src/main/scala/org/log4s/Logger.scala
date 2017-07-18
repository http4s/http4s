package org.log4s

case class Logger(name: String) {
  def warn(s: => String): Unit = {}
  def trace(s: => String): Unit = {}
  def error(t: Throwable)(s: => String): Unit = {}
  def error(t: Any)(s: => String): Unit = {}
}
