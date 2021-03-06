#!/usr/bin/python3

import socket
import json
import threading
import http.server
import os
import sys
import time
import random

BROADCAST_PORT = 42424
WEB_PORT = 9001

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

class RBSocket:
    MUST_ARRIVE_TIMER_PERIOD = 0.05
    MUST_ARRIVE_ATTEMPTS = 15

    def __init__(self, owner):
        self.owner = owner

        self.read_counter = 0
        self.write_counter = 0

        self.recent_received_must_arrives = {}
        self.sent_must_arrives = {}
        self.must_arrive_timer = self.MUST_ARRIVE_TIMER_PERIOD

        self.controller_addr = None

        self._sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self._sock.bind(('', BROADCAST_PORT))
        self._sock.setblocking(False)

    def write(self, data, address):
        #print("SEND %s %s" % (address, data))
        while True:
            try:
                self._sock.sendto(data, address)
                return
            except BlockingIOError:
                continue

    def send(self, address, cmd, withcounter=True, **params):
        params["c"] = cmd
        if withcounter:
            params["n"] = self.write_counter
            self.write_counter += 1
        data = json.dumps(params).encode("utf-8")
        self.write(data, address)

    def send_must_arrive(self, address, cmd, unlimited_attempts=False, **params):
        id = random.randrange(0xFFFFFFFF)
        while id in self.sent_must_arrives:
            id = random.randrange(0xFFFFFFFF)

        params["c"] = cmd
        params["e"] = id
        self.sent_must_arrives[id] = { "payload": params, "attempts": 0 if not unlimited_attempts else None, "address": address }
        self.send(address, cmd, **params)

    def log(self, msg):
        if self.controller_addr:
            self.send_must_arrive(self.controller_addr, "log", msg=msg)

    def update_must_arrives(self, diff):
        if self.must_arrive_timer <= diff:
            ids = list(self.sent_must_arrives.keys())
            for id in ids:
                ctx = self.sent_must_arrives[id]
                self.send(ctx["address"], ctx["payload"]["c"], **ctx["payload"])
                if ctx["attempts"] is not None:
                    ctx["attempts"] += 1
                    if ctx["attempts"] > self.MUST_ARRIVE_ATTEMPTS:
                        del self.sent_must_arrives[id]
            self.must_arrive_timer = self.MUST_ARRIVE_TIMER_PERIOD
        else:
            self.must_arrive_timer -= diff

    def receive_messages(self):
        while True:
            try:
                msg, addr = self._sock.recvfrom(65535)
                self.handle_msg(msg, addr)
            except BlockingIOError:
                return

    def start(self):
        last = time.time()
        while True:
            now = time.time()
            self.update_must_arrives(now - last)
            last = now

            self.receive_messages()

            time.sleep(0.01)

    def handle_msg(self, msg, addr):
        msg = json.loads(msg)
        if msg["c"] == "discover":
            print("%s: %s" % (addr, msg))
            self.send(addr, "found", withcounter=False,
                owner=self.owner, name="Mock", desc="MockingBoard script", path="/", port=WEB_PORT)
            return

        if msg["n"] == -1:
            self.read_counter = 0
            self.write_counter = 0
        elif msg["n"] < self.read_counter and self.read_counter - msg["n"] < 300:
            return
        else:
            self.read_counter = msg["n"]

        if "f" in msg:
            self.send(addr, msg["c"], f=msg["f"])
            if msg["f"] in self.recent_received_must_arrives:
                return
            else:
                self.recent_received_must_arrives[msg["f"]] = time.time()
        elif "e" in msg:
            try:
                del self.sent_must_arrives[msg["e"]]
            except KeyError:
                pass
            return

        if msg["c"] == "possess":
            self.controller_addr = addr
            print("Possesed by %s" % (addr, ))
            self.log("Possessed by %s\n" % addr[0])
        elif msg["c"] == "ping":
            self.send(addr, "pong")
        elif msg["c"] == "joy":
            i = 0
            for j in msg["data"]:
                sys.stdout.write("#j%d %6d %6d | " % (i, j["x"], j["y"]))
                i += 1
        elif msg["c"] == "arm":
            sys.stdout.write(" #a %f %f    \r" % (msg["x"], msg["y"]))
        elif msg["c"] == "fire":
            self.log("Fire!\n")
            print("\n\nFIRE ZE MISSILES!!\n")
        elif msg["c"] == "arminfo":
            info = {
                "height": 60,
                "radius": 110,
                "off_x": 0,
                "off_y": 20,
                "bones": [
                    {
                        "len": 110,
                        "angle": -3.14/2,
                        "rmin": -1.65806,
                        "rmax": 0,
                        "amin": -3.14,
                        "amax": 3.14,
                        "bmin": -3.14,
                        "bmax": 3.14,
                    },
                    {
                        "len": 135,
                        "angle": -3.14/2,
                        "rmin": 0.52,
                        "rmax": 2.87,
                        "amin": -0.34,
                        "amax": 3.14,
                        "bmin": 0.69,
                        "bmax": 2.79,
                    }
                ]
            }
            if self.controller_addr:
                self.send_must_arrive(self.controller_addr, "arminfo", **info)
        else:
            print("\n%s: %s" % (addr, msg))

if __name__ == "__main__":
    ServerThread(daemon=True).start()

    owner="foo"
    if len(sys.argv) > 1:
        owner = sys.argv[1]

    print("This mock device is owned by '%s'." % owner)

    sock = RBSocket(owner)
    sock.start()
