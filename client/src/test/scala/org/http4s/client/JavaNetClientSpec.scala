package org.http4s
package client

import cats.effect.IO

class JavaNetClientSpec
    extends ClientRouteTestBattery("JavaNetClient", JavaNetClient[IO])
