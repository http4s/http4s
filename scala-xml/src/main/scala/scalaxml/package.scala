/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
