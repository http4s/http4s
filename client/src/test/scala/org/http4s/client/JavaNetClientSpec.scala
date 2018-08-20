package org.http4s
package client

import cats.effect.IO
import org.http4s.Http4sSpec._

class JavaNetClientSpec
    extends ClientRouteTestBattery(
      "JavaNetClient",
      JavaNetClient(TestBlockingExecutionContext)
        .create[IO](implicitly, IO.contextShift(TestExecutionContext)))
