/*
 * Copyright 2013 http4s.org
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

package org.http4s.servlet

import javax.servlet.ServletContext
import scala.math.Ordered.orderingToOrdered

final case class ServletApiVersion(major: Int, minor: Int) extends Ordered[ServletApiVersion] {
  override def compare(that: ServletApiVersion): Int =
    (this.major, this.minor).compare((that.major, that.minor))

  override val toString: String = s"$major.$minor"
}

object ServletApiVersion {
  private val JettyRegex = """jetty/(\d+)\.(\d+)\..*""".r

  def apply(sc: ServletContext): ServletApiVersion =
    (sc.getMajorVersion, sc.getMinorVersion) match {
      // https://bugs.eclipse.org/bugs/show_bug.cgi?id=448761#add_comment
      case (3, 0) =>
        sc.getServerInfo match {
          case JettyRegex("9", minor) if minor.toInt >= 1 => ServletApiVersion(3, 1)
          case _ => ServletApiVersion(3, 0)
        }
      case (major, minor) => ServletApiVersion(major, minor)
    }
}
