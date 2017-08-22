# console-agent-app-java

## Running

You can run the console sample by passing in command line arguments as shown in this example:<br>
`java -jar ./build/libs/workspace-sample-1.0-SNAPSHOT.jar --apiKey=<key> --clientId=<clientId> --baseUrl=<url> --authBaseUrl=<optional> --username=<tenant\\username> --password=<p> --debugEnabled --defaultAgentId=<optional> --defaultDn=<optional> --defaultDestination=<optional>`

## Commands

| Command          | Aliases           | Arguments   | Description |
| -------------    |:-----------------:| ----------: |------------------------------ |
| initialize       | init, i           |             | initialize the API using the arguments provided at startup                      |
| destroy          | logout, l         |             | logout and cleanup                      |
| activate-channels | ac                | agentId, dn | activate the voice channel using the provided resources             |
| user             | u                 |             | print information about the user                      |
| configuration    | c                 |             | print configuration returned by the server |
| dn               |                   |             | print the current state of the dn                      |
| calls            |                   |             | print the list of active calls                      |
| ready            | r                 |             | set agent state to ready                      |
| not-ready        | nr                |             | set agent state to notready                      |
| dnd-on           |                   |             | turn do-not-disturb on for voice |
| dnd-off          |                   |             | turn do-not-disturb off for voice |
| voice-logout     |                   |             | logout the voice channel (does not logout the overall session) |
| voice-login      |                   |             | login the voice channel after previously using voice-logout |
| set-forward      |                   | destination | set call forwarding to the specified destination |
| cancel-forward   |                   |             | cancel call forwarding |
| make-call        | mc                | destination | make a call to the specified destination. If not provided the default destination will be used.                      |
| answer           | a                 | id          | answer the specified call (*)                       |
| hold             | h                 | id          | place the specified call on hold. (*)                   |
| retrieve         | ret               | id          | retrieve the specified call (*)                      |
| release          | rel               | id          | release the specified call (*)                      |
| clear-call       |                   | id          | clear the specified call (*) |
| send-dtmf        | dtmf              | id, digits  | send the specified dtmf digits (*) |
| redirect         |                   | id, destination | redirect the call to the specified destination (*) |
| initiate-conference              |ic                   |id, destination            | initiate a conference to the specified destination                      |
| complete-conference              |cc                 |id, parentConnId             | complete a conference (**)                  |
| delete-from-conference| dfc |                        | id, dnToDrop | drop the specififed dn from the conference (*) |
| initiate-transfer              |it                   |id, destination            | initiate a transfer to the specified destination                      |
| complete-transfer              |ct                   |id, parentConnId             | complete a transfer (**)                    |
| alternate              |alt                   |id, heldConnId            | alternate calls                      |
| merge                  |                      |id, otherConnId           | merge calls |
| reconnect              |                      |id, heldConnId | reconnect call |
| single-step-conference |                      |id, destination | perform a single-step conference to the specified destination (*) |
| single-step-transfer   |                      |id, destination | perform a single-step transfer to the specififed destination (*) |
| attach-user-data       | aud                  |id, key, value | attach the specified key/value pair to the call (*) |
| update-user-data       | uud                  |id, key, value | update the specified key/value pair (*) |
| delete-user-data-pair  | dp                   |id, key        | delete the specified user-data key (*) |
| start-recording |    | id | start call recording (*) |
| pause-recording |    | id | pause call recording (*) |
| resume-recording |    | id | resume call recording (*) |
| stop-recording |     | id | stop call recording (*) |
| send-user-event |    | key, value, callUuid | send EventUserEvent with the provided key/value pair and optional callUuid. |
| target-search              |ts                   |searchTerm, limit            | search for targets using the specified search term                      |
| clear              |                   |            | clear the output window                      |
| console-config              |                   |            | print the console config                      |
| exit              |x                   |            | logout if necessary then exit                      |
| debug              |d                  |            | toggle debug output                      |
| help              |?                   |            | print the list of available commands                      |

(*) - if there is only one active call the id parameter can be omitted.<br>
(**) - if there are only two active calls both id and parentId parameters can be omitted.


