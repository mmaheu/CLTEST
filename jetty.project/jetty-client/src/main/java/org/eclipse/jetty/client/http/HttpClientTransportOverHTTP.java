//
//  ========================================================================
//  Copyright (c) 1995-2013 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.client.http;

import java.io.IOException;
import java.util.Map;

import org.eclipse.jetty.client.AbstractHttpClientTransport;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.Origin;
import org.eclipse.jetty.client.api.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Promise;

public class HttpClientTransportOverHTTP extends AbstractHttpClientTransport
{
    public HttpClientTransportOverHTTP()
    {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
    }

    public HttpClientTransportOverHTTP(int selectors)
    {
        super(selectors);
    }

    @Override
    public HttpDestination newHttpDestination(Origin origin)
    {
        return new HttpDestinationOverHTTP(getHttpClient(), origin);
    }

    @Override
    public org.eclipse.jetty.io.Connection newConnection(EndPoint endPoint, Map<String, Object> context) throws IOException
    {

        HttpDestination destination = (HttpDestination)context.get(HTTP_DESTINATION_CONTEXT_KEY);
        HttpConnectionOverHTTP connection = new HttpConnectionOverHTTP(endPoint, destination);
        @SuppressWarnings("unchecked")
        Promise<Connection> promise = (Promise<Connection>)context.get(HTTP_CONNECTION_PROMISE_CONTEXT_KEY);
        promise.succeeded(connection);
        return connection;
    }
}
