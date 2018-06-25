#!/usr/bin/python3

import socket
import json
import threading
import http.server
import os

BROADCAST_PORT = 42424
WEB_PORT = 9000

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        path = "../web/" + self.path
        if self.path == "" or path.endswith("/"):
            path += "index.html"

        if not os.path.isfile(path):
            self.send_response(404)
            self.end_headers()
            self.wfile.write("File not found.\n".encode("utf-8"))
            return

        self.send_response(200)
        self.send_header('Content-type','text/html')
        self.end_headers()

        with open(path, "rb") as f:
            self.wfile.write(f.read())

class ServerThread(threading.Thread):
    def run(self):
        with http.server.HTTPServer(("", WEB_PORT), Handler) as server:
            server.serve_forever()

def sendmsg(sock, cmd, address, **params):
    params["c"] = cmd
    data = json.dumps(params).encode("utf-8")
    sock.sendto(data, address)

if __name__ == "__main__":
    ServerThread(daemon=True).start()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(('', BROADCAST_PORT))

    while True:
        msg, addr = sock.recvfrom(65535)
        print("%s: %s" % (addr, msg.decode("utf-8")))

        msg = json.loads(msg)
        if msg["c"] == "discover":
            sendmsg(sock, "found", addr, name="Mock", desc="MockingBoard script", path="/", port=WEB_PORT)
        elif msg["c"] == "ping":
            sendmsg(sock, "pong", addr)


