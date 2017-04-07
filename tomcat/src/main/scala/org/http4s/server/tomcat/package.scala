package org.http4s.server

import org.apache.catalina.startup.Tomcat

package object tomcat {
  type TomcatConfigurer = Tomcat => Unit
}
