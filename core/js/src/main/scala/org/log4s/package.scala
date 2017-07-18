package org

// Poor's man implementation of log4s for scala.js
package object log4s {
  def getLogger: Logger = Logger(this.getClass.getName)
  def getLogger[A](clazz: Class[A]): Logger = Logger(clazz.getName)
}
