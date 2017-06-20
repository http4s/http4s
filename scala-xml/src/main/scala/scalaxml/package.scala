package org.http4s

import javax.xml.parsers.SAXParserFactory

package object scalaxml extends ElemInstances {
  override val saxFactory = {
    val factory = SAXParserFactory.newInstance
    // Safer parsing settings to avoid certain class of XML attacks
    // See https://github.com/scala/scala-xml/issues/17
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setXIncludeAware(false)
    factory
  }
}
