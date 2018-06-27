function Joystick(elementId, color) {
    var zone = document.getElementById(elementId);

    this.radius = zone.offsetWidth/1.7 / 2;
    this.x = 0;
    this.y = 0;

    this.manager = nipplejs.create({
        zone: zone,
        mode: "static",
        color: color,
        size: this.radius*2,
        position: {
            "top": "50%",
            "left": "50%",
        },
    });

    this.manager.on("move", function(event, data) {
        var dist = data.distance/(this.radius)*32767;
        this.x = Math.cos(data.angle.radian)*dist | 0;
        this.y = Math.sin(data.angle.radian)*dist | 0;
    }.bind(this));

    this.manager.on("end", function(event, data) {
        this.x = 0;
        this.y = 0;
    }.bind(this));
}

function Manager(joysticks) {
    this.socket = null;
    this.pingsSent = 0;
    this.pingsReceived = 0;
    this.joysticks = joysticks;
}

Manager.prototype.start = function(address) {
    this.socket = new ReconnectingWebSocket(address);
    this.socket.addEventListener('open', function (event) {
        setStatus("Connected, sent message");
    });

    this.socket.addEventListener('message', this.onMessage.bind(this));

    requestAnimationFrame(this.update.bind(this));
}

Manager.prototype.update = function() {
    if(this.socket.readyState === WebSocket.OPEN) {
        var data = []
        for(var i = 0; i < this.joysticks.length; ++i) {
            data.push({
                "x": this.joysticks[i].x,
                "y": this.joysticks[i].y,
            })
        }
        this.socket.send(JSON.stringify({ "c": "joy", "data": data }))
    }
    requestAnimationFrame(this.update.bind(this))
}

Manager.prototype.onMessage = function(event) {
    var data = JSON.parse(event.data);
    switch(data["c"]) {
    case "pong": {
        this.pingsReceived++;
        break;
    }
    }
}

Manager.prototype.updatePingCounter = function() {
    document.getElementById("counter").innerHTML =
        "RX: " + this.pingsReceived +
        " TX: " + this.pingsSent +
        " Diff: " + (this.pingsSent - this.pingsReceived);
}

function setStatus(text) {
    document.getElementById("status").innerHTML = text;
}

window.addEventListener("load", function(){
    if(!('WebSocket' in window)) {
        setStatus("WebSockets are not supported on this device!");
        return
    }

    var man = new Manager([
        new Joystick("joy0", "red"),
        new Joystick("joy1", "blue")
    ])
    man.start("ws://localhost:9000");
});
