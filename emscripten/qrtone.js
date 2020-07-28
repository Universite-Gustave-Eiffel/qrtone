var audioCtx = new (window.AudioContext || window.webkitAudioContext)();
function play() {
    let qrtone = _qrtone_new();
    let content = new Uint8Array([0, 7, 78, 105, 99, 111, 108, 97, 115, 1, 5, 72, 101, 108, 108, 111]);
    _qrtone_init(qrtone, audioCtx.sampleRate);

    let contenti8 = allocate(content, 'i8', ALLOC_NORMAL);

    let nsamples = _qrtone_set_payload(qrtone, contenti8, content.length);

    let signalAlloc = Module._malloc(nsamples * 4);

    _memset(signalAlloc, 0, nsamples * 4);

    _qrtone_get_samples(qrtone, signalAlloc, nsamples, 1.0);

    let signal = Module.HEAPF32.subarray(signalAlloc / 4, signalAlloc / 4 + nsamples * 4);

    _free(signalAlloc);

    _qrtone_free(qrtone);


    let channels = 1;

    // Create an empty two second stereo buffer at the
    // sample rate of the AudioContext
    var frameCount = nsamples;

    //document.getElementById("demo").innerHTML += "sampleRate: "+audioCtx.sampleRate+" Hz<br>"+
    //"content length: "+ content.length+"<br>"+
    //"words length: "+ wordLength+"<br>"+
    //" Play time "+(signalSize/audioCtx.sampleRate)+" s<br>";

    var myAudioBuffer = audioCtx.createBuffer(channels, frameCount, audioCtx.sampleRate);
    var nanvals = 0;
    for (var channel = 0; channel < channels; channel++) {

        var nowBuffering = myAudioBuffer.getChannelData(channel);
        for (var i = 0; i < frameCount; i++) {
            // audio needs to be in [-1.0; 1.0]
            nowBuffering[i] = Module.getValue(signalAlloc + ( i * 4), 'float');
        }
    }

    // Get an AudioBufferSourceNode.
    // This is the AudioNode to use when we want to play an AudioBuffer
    var source = audioCtx.createBufferSource();
    // set the buffer in the AudioBufferSourceNode
    source.buffer = myAudioBuffer;
    // connect the AudioBufferSourceNode to the
    // destination so we can hear the sound
    source.connect(audioCtx.destination);
    // start the source playing
    source.start();
}


///**
// * JS Code derived from QietJS 3-clause BSD code
// */
//
//var QRTone = (function() {
//    // initialization flags
//    var emscriptenInitialized = false;
//
//    // our local instance of window.AudioContext
//    var audioCtx;
//
//    // consumer callbacks. these fire once QRTone is ready to create transmitter/receiver
//    var readyCallbacks = [];
//    var readyErrbacks = [];
//    var failReason = "";
//
//    // isReady tells us if we can start creating transmitters and receivers
//    // we need the emscripten portion to be running and we need our
//    // async fetch of the profiles to be completed
//    function isReady() {
//        return emscriptenInitialized;
//    };
//
//    function isFailed() {
//        return failReason !== "";
//    };
//
//    // start gets our AudioContext and notifies consumers that QRTone can be used
//    function start() {
//        var len = readyCallbacks.length;
//        for (var i = 0; i < len; i++) {
//            readyCallbacks[i]();
//        }
//    };
//
//    function initAudioContext() {
//        if (audioCtx === undefined) {
//            audioCtx = new (window.AudioContext || window.webkitAudioContext)();
//            console.log(audioCtx.sampleRate);
//        }
//    };
//
//    function fail(reason) {
//        failReason = reason;
//        var len = readyErrbacks.length;
//        for (var i = 0; i < len; i++) {
//            readyErrbacks[i](reason);
//        }
//    };
//
//    function checkInitState() {
//        if (isReady()) {
//            start();
//        }
//    };
//
//    // this is intended to be called only by emscripten
//    function onEmscriptenInitialized() {
//        emscriptenInitialized = true;
//        checkInitState();
//    };
//
//    /**
//     * Add a callback to be called when QRTone is ready for use, e.g. when transmitters and receivers can be created.
//     * @function addReadyCallback
//     * @memberof QRTone
//     * @param {function} c - The user function which will be called
//     * @param {onError} [onError] - User errback function
//     * @example
//     * addReadyCallback(function() { console.log("ready!"); });
//     */
//    function addReadyCallback(c, errback) {
//        if (isReady()) {
//            c();
//            return;
//        }
//        readyCallbacks.push(c);
//        if (errback !== undefined) {
//            if (isFailed()) {
//                errback(failReason);
//                return;
//            }
//            readyErrbacks.push(errback);
//        }
//    };
//
//    // receiver functions
//
//    function audioInputReady() {
//        var len = audioInputReadyCallbacks.length;
//        for (var i = 0; i < len; i++) {
//            audioInputReadyCallbacks[i]();
//        }
//    };
//
//    function audioInputFailed(reason) {
//        audioInputFailedReason = reason;
//        var len = audioInputFailedCallbacks.length;
//        for (var i = 0; i < len; i++) {
//            audioInputFailedCallbacks[i](audioInputFailedReason);
//        }
//    };
//
//    function addAudioInputReadyCallback(c, errback) {
//        if (errback !== undefined) {
//            if (audioInputFailedReason !== "") {
//                errback(audioInputFailedReason);
//                return
//            }
//            audioInputFailedCallbacks.push(errback);
//        }
//        if (audioInput instanceof MediaStreamAudioSourceNode) {
//            c();
//            return
//        }
//        audioInputReadyCallbacks.push(c);
//    }
//
//    function gUMConstraints() {
//        if (navigator.webkitGetUserMedia !== undefined) {
//            return {
//                audio: {
//                    optional: [
//                      {googAutoGainControl: false},
//                      {googAutoGainControl2: false},
//                      {echoCancellation: false},
//                      {googEchoCancellation: false},
//                      {googEchoCancellation2: false},
//                      {googDAEchoCancellation: false},
//                      {googNoiseSuppression: false},
//                      {googNoiseSuppression2: false},
//                      {googHighpassFilter: false},
//                      {googTypingNoiseDetection: false},
//                      {googAudioMirroring: false},
//                      {autoGainControl: false},
//                      {noiseSuppression: false}
//                    ]
//                }
//            };
//        }
//        if (navigator.mozGetUserMedia !== undefined) {
//            return {
//                audio: {
//                    echoCancellation: false,
//                    mozAutoGainControl: false,
//                    mozNoiseSuppression: false,
//                    autoGainControl: false,
//                    noiseSuppression: false
//                }
//            };
//
//        }
//        return {
//            audio: {
//                echoCancellation: false,
//                optional: [
//                  {autoGainControl: false},
//                  {noiseSuppression: false}
//                ]
//            }
//        };
//    };
//
//
//    function createAudioInput() {
//        audioInput = 0; // prevent others from trying to create
//        window.setTimeout(function() {
//            gUM.call(navigator, gUMConstraints(),
//                function(e) {
//                    audioInput = audioCtx.createMediaStreamSource(e);
//
//                    // stash a very permanent reference so this isn't collected
//                    window.qrtone_receiver_anti_gc = audioInput;
//
//                    audioInputReady();
//                }, function(reason) {
//                    audioInputFailed(reason.name);
//                });
//        }, 0);
//    };
//
//    /**
//     * Disconnect QRTone.js from its microphone source
//     * This will disconnect QRTone.js's microphone fully from all receivers
//     * This is useful to cause the browser to stop displaying the microphone icon
//     * Browser support is limited for disconnecting a single destination, so this
//     * call will disconnect all receivers.
//     * It is highly recommended to call this only after destroying any receivers.
//     * @function disconnect
//     */
//    function disconnect() {
//        if (audioInput !== undefined) {
//            audioInput.disconnect();
//            audioInput = undefined;
//            delete window.qrtone_receiver_anti_gc;
//        }
//    };
//    function transmitter(opts) {
//        initAudioContext();
//        var qrtone = _qrtone_new();
//        _qrtone_init(qrtone, audioCtx.sampleRate);
//        var destroyed = false;
//    }
//
//    return {
//        emscriptenInitialized: onEmscriptenInitialized,
//        addReadyCallback: addReadyCallback,
//        disconnect: disconnect
//    };
//})();
//
//
//Module['onRuntimeInitialized'] = QRTone.emscriptenInitialized;
//
