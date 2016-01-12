package org.http4s.client.asynchttpclient

import org.htt4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.client.ClientRouteTestBattery

class AsyncHttpClientSpec extends ClientRouteTestBattery("AsyncHttpClient", AsyncHttpClient())
