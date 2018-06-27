#!/usr/bin/python3

import socket
import json
import threading
import http.server
import os
import sys

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

writecounter = 0
def sendmsg(sock, cmd, address, withcounter=True, **params):
    global writecounter

    params["c"] = cmd
    if withcounter:
        params["n"] = writecounter
        writecounter += 1
    data = json.dumps(params).encode("utf-8")
    sock.sendto(data, address)

if __name__ == "__main__":
    ServerThread(daemon=True).start()

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(('', BROADCAST_PORT))

    readcounter = 0
    writecounter = 0
    while True:
        msg, addr = sock.recvfrom(65535)

        msg = json.loads(msg)
        if msg["c"] == "discover":
            print("%s: %s" % (addr, msg))
            sendmsg(sock, "found", addr, withcounter=False,
                name="Mock", desc="MockingBoard script", path="/", port=WEB_PORT)
            continue

        if msg["n"] == -1:
            readcounter = 0
            writecounter = 0
        elif msg["n"] < readcounter and readcounter - msg["n"] < 300:
            print("ignore")
            continue
        else:
            readcounter = msg["n"]

        if msg["c"] == "ping":
            sendmsg(sock, "pong", addr)
        elif msg["c"] == "joy":
            i = 0
            sys.stdout.write("Joy: ")
            for j in msg["data"]:
                sys.stdout.write("#%d %6d %6d | " % (i, j["x"], j["y"]))
                i += 1
            sys.stdout.write("\r")
