package org.http4s.client.blaze

import org.http4s.client.ClientRouteTestBattery


class BlazePooledHttp1ClientSpec extends ClientRouteTestBattery("Blaze PooledHttp1Client", PooledHttp1Client())

