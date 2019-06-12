
package org.http4s.internal

import scala.collection.convert.{DecorateAsJava , DecorateAsScala}

private[http4s] object CollectionConverters extends DecorateAsJava with DecorateAsScala
