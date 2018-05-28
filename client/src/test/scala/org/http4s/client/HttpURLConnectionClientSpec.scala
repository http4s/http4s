package org.http4s
package client

import cats.effect.IO

class HttpURLConnectionClientSpec
    extends ClientRouteTestBattery("HttpURLConnectionClient", HttpURLConnectionClient[IO])
