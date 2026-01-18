/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.app.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class EngineProxyController {

    @Value("${seatunnel.engine.url:http://localhost:8080}")
    private String engineUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/**")
    public ResponseEntity<String> proxyGet(HttpServletRequest request) {
        // Get the path after /api
        String requestUri = request.getRequestURI();
        String path = requestUri.substring("/api".length());

        // Append query string if present
        String queryString = request.getQueryString();
        String targetUrl = engineUrl + path;
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        // Forward headers
        HttpHeaders headers = new HttpHeaders();
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            // Skip hop-by-hop headers
            if (!headerName.equalsIgnoreCase("Host")
                    && !headerName.equalsIgnoreCase("Connection")
                    && !headerName.equalsIgnoreCase("Content-Length")) {
                headers.set(headerName, request.getHeader(headerName));
            }
        }
        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(targetUrl, HttpMethod.GET, entity, String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Error proxying request to engine: " + e.getMessage());
        }
    }
}
