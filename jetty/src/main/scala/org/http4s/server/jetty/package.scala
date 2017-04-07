package org.http4s.server

import org.eclipse.jetty.server.{Server => JServer}

package object jetty {
  type JettyConfigurer = JServer => Unit
}
