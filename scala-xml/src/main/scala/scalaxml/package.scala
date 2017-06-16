package org.http4s

import javax.xml.parsers.SAXParserFactory

package object scalaxml extends ElemInstances {
  override val saxFactory = SAXParserFactory.newInstance
}
