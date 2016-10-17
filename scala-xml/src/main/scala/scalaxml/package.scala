package org.http4s

import scala.xml._
import scala.xml.factory._
import scala.xml.parsing._

package object scalaxml extends ElemInstances {
  /**
   * Backport of https://github.com/scala/scala-xml/pull/34. Can be
   * deprecated after we drop Scala < 2.11.5.
   */
  val Http4sXmlLoader: XMLLoader[Elem] =
    new XMLLoader[Elem] {
      override def adapter: FactoryAdapter =
        new NoBindingFactoryAdapter {
          override def processingInstruction(target: String, data: String) {
            captureText()
            hStack pushAll createProcInstr(target, data)
          }
        }
    }
}
