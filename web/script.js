'use strict';

function Joystick(elementId, color, buttonText, buttonClickHandler) {
    var zone = document.getElementById(elementId);

    this.radius = zone.offsetWidth/1.7 / 2;
    this.x = 0;
    this.y = 0;
    this.buttonClickHandler = buttonClickHandler;
    this.touchStart = null;

    this.manager = nipplejs.create({
        zone: zone,
        mode: "static",
        color: color,
        size: this.radius*2,
        position: {
            "top": "50%",
            "left": "50%",
        },
        restOpacity: 0.9,
        fadeTime: 0,
    });

    if(buttonText) {
        var joy = this.manager.get(this.manager.id);
        var nipple = joy.ui.front;
        nipple.innerHTML = "FIRE!"
        nipple.style.fontWeight = "bold";
        nipple.style.textAlign = "center";
        nipple.style.verticalAlign = "middle";
        nipple.style.lineHeight = nipple.style.height;
    }

    this.manager.on("move", function(event, data) {
        var dist = data.distance/(this.radius)*32767;
        this.x = Math.cos(data.angle.radian)*dist | 0;
        this.y = Math.sin(data.angle.radian)*dist | 0;
    }.bind(this));

    this.manager.on("start", function(event, data) {
        this.touchStart = Date.now();
        this.x = 0;
        this.y = 0;
    }.bind(this));

    this.manager.on("end", function(event, data) {
        var diff = Date.now() - this.touchStart;
        if(this.buttonClickHandler && diff < 200 && Math.abs(this.x) < 3000 && Math.abs(this.y) < 3000) {
            this.buttonClickHandler();
        }

        this.x = 0;
        this.y = 0;
    }.bind(this));
}

function Manager() {
    this.socket = null;
    this.pingsSent = 0;
    this.pingsReceived = 0;
    this.joysticks = [];

    this.mustArriveCommands = {};
    this.MUST_ARRIVE_TIMER_FULL = 50;
    this.MUST_ARRIVE_RETRIES = 15;
    this.mustArriveTimer = this.MUST_ARRIVE_TIMER_FULL;
}

Manager.prototype.addJoystick = function(joy) {
    this.joysticks.push(joy);
}

Manager.prototype.start = function(address) {
    this.socket = new ReconnectingWebSocket(address);
    this.socket.addEventListener('open', function (event) {
        setStatus("Connected, sent message");
    });

    this.socket.addEventListener('message', this.onMessage.bind(this));

    this.lastUpdate = Date.now();
    requestAnimationFrame(this.update.bind(this));
}

Manager.prototype.update = function() {
    var now = Date.now();
    var diff = (now - this.lastUpdate);
    this.lastUpdate = now;

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

    if(diff >= this.mustArriveTimer) {
        for (var id in this.mustArriveCommands) {
            if (this.mustArriveCommands.hasOwnProperty(id)) {
                var info = this.mustArriveCommands[id];
                this.socket.send(info.payload);
                if(++info.attempts >= this.MUST_ARRIVE_RETRIES) {
                    delete this.mustArriveCommands[id];
                }
            }
        }
        this.mustArriveTimer = this.MUST_ARRIVE_TIMER_FULL;
    } else {
        this.mustArriveTimer -= diff;
    }

    requestAnimationFrame(this.update.bind(this))
}

Manager.prototype.onMessage = function(event) {
    var data = JSON.parse(event.data);
    if("f" in data) {
        delete this.mustArriveCommands[data["f"]];
    }

    switch(data["c"]) {
    case "pong": {
        this.pingsReceived++;
        break;
    }
    }
}

Manager.prototype.sendMustArrive = function(command, data) {
    var id = 0;
    do {
        id = (Math.random() * 0xFFFFFFFF) | 0;
    } while(id in this.mustArriveCommands);

    data["c"] = command;
    data["f"] = id;

    var payload = JSON.stringify(data);
    this.mustArriveCommands[id] = { "payload": payload, "attempts": 0 };
    this.socket.send(payload);
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

    var man = new Manager();
    man.addJoystick(new Joystick("joy0", "blue"));
    man.addJoystick(new Joystick("joy1", "red", "FIRE!", function() {
        man.sendMustArrive("fire", {});
    }));

    man.start("ws://localhost:9000");
});
