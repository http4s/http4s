package org.http4s
package client
package blaze

import org.http4s.client.ClientRouteTestBattery

class BlazeSimpleHttp1ClientSpec extends ClientRouteTestBattery("SimpleHttp1Client", SimpleHttp1Client(BlazeClientConfig.defaultConfig))
