package org.http4s
package server

import java.net.InetSocketAddress

trait Server[F[_]] {
  def shutdown: F[Unit]

  def address: InetSocketAddress
}
