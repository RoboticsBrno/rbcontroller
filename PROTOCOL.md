# Protocol
It's sending JSONs over UDP. Two sides, master & slave. The phone is the master, robot is the slave.

## Packet Object fields
| Name    | Type     | Precence?    | Description                              |
| ------- | -------- | ------------ | ---------------------------------------- |
| c       | `string` | **REQUIRED** | The command name                         |
| n       | `number` | **REQUIRED** | Packet counter                           |
| f       | `number` | **OPTIONAL** | MustArrive packet id sent by master   |
| e       | `number` | **OPTIONAL** | MustArrive packet id sent by slave    |
| *other* | `any`    | **OPTIONAL** | The packet data                          |

### Packet counter
When sending a packet, the device MUST set a `n` field to an integer higher than `n` in the previous packet or zero. The master and slave packet counters are independent. Any packets arriving out of order MUST be discarded.

## Device Discovery
When looking for devices, the master will send following packet as a broadcast to `255.255.255.0:42424`. The `discover` and `found` commands are the only packet that MUST NOT have the packet counter. Slaves MUST always respond to the `discover` packet.

```json
M: { "c": "discover" }
S: { "c": "found", "name": "Robot McRobotface", "desc": "The Best Robot", "path": "/", "port": 80}
```

The `path` and `port` fields specify the path on the web server with the control page and the port to use. They are OPTIONAL and default to `/` and `80` respectively.

## MustArrive mechanism
We need to ensure some packets really do arrive. The mechanism is simple, just re-send the packet until a response arrives.

Each MustArrive packets MUST have a random 32 bit unsigned integer set as `f` or `e` field, to identify this particular packet.

When master receives packet with `e` field set, it MUST respond with the same packet back. The packet data MAY be stripped.

When slave receives packet with `f` field set, it MUST respond with the same packet back. The packet data MAY be stripped.

If the sender does not receive a confirmation, it MUST re-send the packet, with packet counter appropriatelly increased.

### Example
Master -> Slave:
```json
M: { "c": "fire", "n": 42, "f": 374563 }
<packet lost>
M: { "c": "fire", "n": 43, "f": 374563 }
S: { "c": "fire", "n": 12, "f": 374563 }
```

Slave -> Master:
```json
S: { "c": "log", "n": 10, "e": 907509, "msg": "log message" }
<packet lost>
S: { "c": "log", "n": 11, "e": 907509, "msg": "log message" }
M: { "c": "log", "n": 789, "e": 907509 }
```

## Control session
Once discovered, the master will start controlling the slave.

### Possess
The `possess` command MUST be the first thing sent. It tells the slave which UDP address and port is used by the master. It uses the MustArrive mechanism.

```json
M: { "c": "possess", "n": 0, "f": 214312 }
S: { "c": "possess", "n": 0, "f": 214312 }
```

### Joystick data
The `joy` command will be send periodically as fast as possible from the master to the slave. The `x` and `x` range is <-32767;32767>.

```json
{
    "c": "joy",
    "n": 241,
    "data": [
        {
            "x": 0,
            "y": 0
        },
        {
            "x": 213,
            "y": 923
        }
    ]
}

{
    "c": "joy",
    "n": 242,
    "data": [
        {
            "x": -25123,
            "y": 531
        },
        {
            "x": 12,
            "y": 213
        }
    ]
}
```

### FIRE command
The command `fire` is used to fire the cannon. It uses the MustArrive mechanism.
```json
M: { "c": "fire", "n": 2131, "f": 973 }
S: { "c": "fire", "n": 90, "f": 973 }
```

### Logging
The slave MAY send `log` commmands to the master. They use the MustArrive mechanism.

```json
S: { "c": "log", "n": 98, "e": 86490, "msg": "Everything's working nicely!" }
M: { "c": "log", "n": 927, "e": 86490 }
```

