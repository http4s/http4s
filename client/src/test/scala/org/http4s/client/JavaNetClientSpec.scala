package org.http4s
package client

import org.http4s.Http4sSpec._

class JavaNetClientSpec
    extends ClientRouteTestBattery(
      "JavaNetClient",
      JavaNetClientBuilder(TestBlockingExecutionContext).resource(implicitly, TestContextShift))
