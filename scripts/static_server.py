#!/usr/bin/env python3

from http import server

class MyHTTPRequestHandler(server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Service-Worker-Allowed", "/")
        server.SimpleHTTPRequestHandler.end_headers(self)        

server.test(HandlerClass=MyHTTPRequestHandler, port=8889)
    