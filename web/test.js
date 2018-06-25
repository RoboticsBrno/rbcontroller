var SOCKET_SERVER = "ws://localhost:9000";

function setStatus(text) {
    document.getElementById("status").innerHTML = text;
}

var socket = null;
var pingsSent = 0;
var pingsReceived = 0;

function sendPing() {
    if(socket.readyState === WebSocket.OPEN) {
        updatePingCounter();
        pingsSent++;
        socket.send('{"c":"ping"}')
    }
    requestAnimationFrame(sendPing)
}

function updatePingCounter() {
    document.getElementById("counter").innerHTML =
        "RX: " + pingsReceived +
        " TX: " + pingsSent +
        " Diff: " + (pingsSent - pingsReceived);
}

window.addEventListener("load", function(){
    if(!('WebSocket' in window)) {
        setStatus("WebSockets are not supported on this device!");
        return
    }

    socket = new ReconnectingWebSocket(SOCKET_SERVER);
    socket.addEventListener('open', function (event) {
        setStatus("Connected, sent message");
    });

    socket.addEventListener('message', function (event) {
        var data = JSON.parse(event.data);
        switch(data["c"]) {
        case "pong": {
            pingsReceived++;
            break;
        }
        }
    });

    requestAnimationFrame(sendPing)
});