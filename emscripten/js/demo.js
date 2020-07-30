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
    var ecc_level = 0; // forward correction error level (0=low 3=max)
    var addCRC = 0; // add 16 bits crc if != 0
    var tx = QRTone.transmitter({onFinish: function () { console.log("transmission complete"); }});
    tx.transmit([colorPicker.color.rgb.r, colorPicker.color.rgb.g, colorPicker.color.rgb.b], ecc_level, addCRC);
}

function changeReceiveMode() {
  if($("#radio-enable-receiver")[0].checked) {
    console.log("enabled");
    var rx = QRTone.receiver({onReceive: function(payload, sampleIndex) { console.log("received chunk of data: " + payload); }});
  } else {
    console.log("disabled");
    if (typeof rx !== "undefined") {
        rx.destroy();
    }
  }
}

var colorPicker = new iro.ColorPicker('#picker');
$( function() {
$( "#tabs" ).tabs({ active: 0 });
} );

$( function() {
$( "input" ).filter(":radio").checkboxradio();
} );
