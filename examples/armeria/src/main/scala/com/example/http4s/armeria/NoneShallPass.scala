/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.http4s.armeria

import com.linecorp.armeria.common.{HttpRequest, HttpResponse, HttpStatus}
import com.linecorp.armeria.server.{HttpService, ServiceRequestContext, SimpleDecoratingHttpService}

class NoneShallPass(delegate: HttpService) extends SimpleDecoratingHttpService(delegate) {

  override def serve(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse =
    HttpResponse.of(HttpStatus.FORBIDDEN)
}
