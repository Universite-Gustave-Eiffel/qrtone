function sendChat() {
    // Convert input to int array
    var ecc_level = 2; // forward correction error level (0=low 3=max)
    var addCRC = 1; // add 16 bits crc if != 0
    const encoder = new TextEncoder()
    var user = Array.from(encoder.encode($('input[name=fname]').val()).values());
    var message = Array.from(encoder.encode($('textarea[name=fmessage]').val()).values());
    var bytes = [0, user.length].concat(user).concat([1, message.length]).concat(message);

    // Send data
    var tx = QRTone.transmitter({onFinish: function () { console.log("transmission complete"); }});
    tx.transmit(bytes, ecc_level, addCRC);
}

function sendColor() {

}

var colorPicker = new iro.ColorPicker('#picker');
$( function() {
$( "#tabs" ).tabs();
} );