var SOCKET_SERVER = "ws://localhost:9000";

function setStatus(text) {
    document.getElementById("status").innerHTML = text;
}

window.addEventListener("load", function(){
    if(!('WebSocket' in window)) {
        setStatus("WebSockets are not supported on this device!");
        return
    }

    const socket = new WebSocket(SOCKET_SERVER);
    socket.addEventListener('open', function (event) {
        setStatus("Connected, sent message");
        socket.send('Hello Server!');
    });

    socket.addEventListener('message', function (event) {
        setStatus("Got message " + event.data);
        console.log('Message from server ', event.data);
    });
});