package org.http4s.servlet

import javax.servlet.ServletContext

import scala.math.Ordered.orderingToOrdered

case class ServletApiVersion (major: Int, minor: Int) extends Ordered[ServletApiVersion] {
  override def compare(that: ServletApiVersion): Int = (this.major, this.minor) compare ((that.major, that.minor))

  override val toString = s"$major.$minor"
}

object ServletApiVersion {
  private val JettyRegex = """jetty/(\d+)\.(\d+)\..*""".r

  def apply(sc: ServletContext): ServletApiVersion = (sc.getMajorVersion, sc.getMinorVersion) match {
    // https://bugs.eclipse.org/bugs/show_bug.cgi?id=448761#add_comment
    case (3, 0) =>
      sc.getServerInfo match {
        case JettyRegex("9", minor) if minor.toInt >= 1 => ServletApiVersion(3, 1)
        case _ => ServletApiVersion(3, 0)
      }
    case (major, minor) => ServletApiVersion(major, minor)
  }
}
