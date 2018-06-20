#!/usr/bin/python3

import socket
import json

def sendmsg(sock, cmd, address, **params):
    params["c"] = cmd
    data = json.dumps(params).encode("utf-8")
    sock.sendto(data, address)

if __name__ == "__main__":
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind(('', 42424))

    while True:
        msg, addr = sock.recvfrom(65535)
        print("%s: %s" % (addr, msg.decode("utf-8")))

        msg = json.loads(msg)
        if msg["c"] == "discover":
            sendmsg(sock, "found", addr, name="mock", desc="MockingBoard script")


