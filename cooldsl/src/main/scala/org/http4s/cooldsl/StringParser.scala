package org.http4s.cooldsl

import scalaz.{-\/, \/-, \/}

/**
 * Created by Bryce Anderson on 4/27/14.
 */

trait StringParser[T] {
  def parse(s: String): \/[String, T]
}

object StringParser {

  ////////////////////// Default parsers //////////////////////////////

  implicit val intParser = new StringParser[Int] {
    override def parse(s: String): \/[String,Int] =
      try \/-(s.toInt)
      catch { case e: NumberFormatException => -\/(s"Invalid Number Format: $s") }
  }

  implicit val strParser = new StringParser[String] {
    override def parse(s: String): \/[String,String] = \/-(s)
  }

}
