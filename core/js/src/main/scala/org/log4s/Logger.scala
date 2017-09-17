package org.log4s

case class Logger(name: String) {
  def trace(s: => String): Unit = {}
  def debug(s: => String): Unit = {}
  def info(s: => String): Unit = {}
  def warn(s: => String): Unit = {}
  def error(t: Throwable)(s: => String): Unit = {}
  def error(t: Any)(s: => String): Unit = {}
}
