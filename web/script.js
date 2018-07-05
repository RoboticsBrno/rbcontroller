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
        if(this.buttonClickHandler && diff < 150 && Math.abs(this.x) < 8000 && Math.abs(this.y) < 8000) {
            this.buttonClickHandler();
        }

        this.x = 0;
        this.y = 0;
    }.bind(this));
}

function Log(elementId) {
    this.el = document.getElementById(elementId);
    this.open = false;
    this.isTouched = false;

    this.el.addEventListener("click", this.onClick.bind(this));

    this.el.addEventListener("touchstart", function() {
        this.isTouched = true;
    }.bind(this));
    this.el.addEventListener("touchend", function() {
        this.isTouched = false;
    }.bind(this));

    this.scrollToBottom();
}

Log.prototype.onClick = function() {
    this.open = !this.open;
    if(this.open) {
        this.el.classList.replace("log-short", "log-full")
    } else {
        this.el.classList.replace("log-full", "log-short")
    }
    this.scrollToBottom();
}

Log.prototype.scrollToBottom = function() {
    this.el.scrollTop = this.el.scrollHeight;
}

Log.prototype.clear = function() {
    this.el.textContent = "";
}

Log.prototype.write = function(msg, noNewLine) {
    if(noNewLine !== true && !msg.endsWith("\n")) {
        msg += "\n";
    }
    this.el.textContent += msg;
    if(!this.isTouched) {
        this.scrollToBottom();
    }
}

function Manager(logElementId) {
    this.socket = null;
    this.joysticks = [];

    this.mustArriveCommands = {};
    this.MUST_ARRIVE_TIMER_FULL = 50;
    this.MUST_ARRIVE_RETRIES = 15;
    this.mustArriveTimer = this.MUST_ARRIVE_TIMER_FULL;
    this.recentMustArriveCommands = {};

    this.log = new Log(logElementId);
}

Manager.prototype.addJoystick = function(joy) {
    this.joysticks.push(joy);
}

Manager.prototype.start = function(address) {
    this.log.write("Connecting to " + address + "... ", true);

    if(!('WebSocket' in window)) {
        this.log.write("\nWebSockets are not supported on this device!");
        return
    }

    this.socket = new ReconnectingWebSocket(address);
    this.socket.addEventListener('open', function (event) {
        this.log.write("connected!")
        this.log.write("Attempting to possess the robot...")
        this.sendMustArrive("possess", {}, true);
    }.bind(this));
    
    this.socket.addEventListener('error', function(event) {
        this.log.write("Connection FAILED!")
    }.bind(this));

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
                if(info.attempts !== null) {
                    if(++info.attempts >= this.MUST_ARRIVE_RETRIES) {
                        delete this.mustArriveCommands[id];
                    }
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
        return;
    } else if("e" in data) {
        this.socket.send(JSON.stringify({"c": data["c"], "e": data["e"]}));
        if(data["e"] in this.recentMustArriveCommands) {
            return;
        } else {
            this.recentMustArriveCommands[data["e"]] = Date.now();
        }
    }

    switch(data["c"]) {
    case "pong":
        break;
    case "log":
        this.log.write(data["msg"]);
        break;
    }
}

Manager.prototype.sendMustArrive = function(command, data, unlimitedAttempts) {
    var id = 0;
    do {
        id = (Math.random() * 0xFFFFFFFF) | 0;
    } while(id in this.mustArriveCommands);

    data["c"] = command;
    data["f"] = id;

    var payload = JSON.stringify(data);
    this.mustArriveCommands[id] = { "payload": payload, "attempts": (unlimitedAttempts !== true) ? 0 : null };
    this.socket.send(payload);
}

Manager.prototype.flashBody = function() {
    var body = document.getElementById("body");
    body.style.backgroundColor = "#ff5454";
    setTimeout(function() {
        body.style.backgroundColor = "white";
    }, 50);
}

window.addEventListener("load", function(){
    var man = new Manager("log");
    man.addJoystick(new Joystick("joy0", "blue"));
    man.addJoystick(new Joystick("joy1", "red", "FIRE!", function() {
        man.flashBody();
        man.sendMustArrive("fire", {});
    }));

    man.start("ws://localhost:9000");
});
