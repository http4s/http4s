package org.http4s.ember.core.h2

import fs2.io.ClosedChannelException
import fs2.io.net.SocketException
import org.http4s.Http4sSuite

class H2ConnectionSuite extends Http4sSuite {

  test("adaptSocketWriteFailure converts ClosedChannelException to SocketException with cause") {
    val input = new ClosedChannelException
    val adapted = H2Connection.adaptSocketWriteFailure(input)

    assert(adapted.isInstanceOf[SocketException])
    assertEquals(adapted.getMessage, "Socket closed when attempting to write")
    assertEquals(adapted.getCause, input)
  }

  test("adaptSocketWriteFailure leaves other failures unchanged") {
    val boom = new RuntimeException("boom")
    val adapted = H2Connection.adaptSocketWriteFailure(boom)
    assertEquals(adapted, boom)
  }
}
