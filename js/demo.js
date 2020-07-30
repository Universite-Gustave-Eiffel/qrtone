function sendChat() {
    // Convert input to int array
    var ecc_level = 2; // forward correction error level (0=low 3=max)
    var addCRC = 1; // add 16 bits crc if != 0
    const encoder = new TextEncoder()
    var user = Array.from(encoder.encode($('input[name=fname]').val()).values());
    var message = Array.from(encoder.encode($('textarea[name=fmessage]').val()).values());
    var bytes = [0, user.length].concat(user).concat([1, message.length]).concat(message);

    // Send data
    if (typeof tx !== "undefined") {
        tx.destroy();
    }
    var tx = QRTone.transmitter({onFinish: function () { console.log("transmission complete"); }});
    tx.transmit(bytes, ecc_level, addCRC);
}

function sendColor() {
    // Send data
    var tx = QRTone.transmitter({onFinish: function () { console.log("transmission complete"); }});
    tx.transmit([colorPicker.color.rgb.r, colorPicker.color.rgb.g, colorPicker.color.rgb.b]);
}

function onNewMessage(payload, epoch) {
    var d = new Date(0);
    d.setUTCSeconds(epoch);
    var user = "";
    var message = "";
    if(payload.length > 4) {
        // Parse message
        let utf8decoder = new TextDecoder();
        var cursor = 0;
        while(cursor < payload.length) {
            if(payload[cursor] == 0 && cursor + 1 < payload.length && cursor + payload[cursor + 1] < payload.length ) {
                user = utf8decoder.decode(new Uint8Array(payload.slice(cursor + 2, cursor + 2 + payload[cursor + 1])));
                cursor += payload[cursor + 1] + 2;
            } else  if(payload[cursor] == 1 && cursor + 1 < payload.length && cursor + payload[cursor + 1] < payload.length ) {
                message = utf8decoder.decode(new Uint8Array(payload.slice(cursor + 2, cursor + 2 + payload[cursor + 1])));
                cursor += payload[cursor + 1] + 2;
            }
        }
    }
    if(user == "") {
        $("#receivedMessage")[0].value += d.toTimeString() + ": " + payload + "\n"
    } else {
        $("#receivedMessage")[0].value += d.toTimeString() + ": " + user + " says \""+ message +"\"\n"
    }
}

function changeReceiveMode() {
  if($("#radio-enable-receiver")[0].checked) {
    console.log("enabled");
    rx = QRTone.receiver({onReceive: onNewMessage});
  } else {
    console.log("disabled");
    if (typeof rx !== "undefined") {
        rx.destroy();
        QRTone.disconnect();
    }
  }
}
var rx;
var tx;
var colorPicker = new iro.ColorPicker('#picker');
$( function() {
$( "#tabs" ).tabs({ active: 0 });
} );

$( function() {
$( "input" ).filter(":radio").checkboxradio();
} );
