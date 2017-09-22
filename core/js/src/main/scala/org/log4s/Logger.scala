package org.log4s

case class Logger(name: String) {
  def trace(s: => String): Unit =
    println(s)

  def debug(s: => String): Unit =
    println(s)

  def info(s: => String): Unit =
    println(s)

  def warn(s: => String): Unit =
    println(s)

  def error(t: Throwable)(s: => String): Unit = {
    t.printStackTrace()
    println(s)
  }

  def error(s: => String): Unit =
    println(s)
}
