/**
 * JS Code derived from QuietJS 3-clause BSD code
 */

var QRTone = (function() {

    // sampleBufferSize is the number of audio samples we'll write per onaudioprocess call
    // must be a power of two. we choose the absolute largest permissible value
    // we implicitly assume that the browser will play back a written buffer without any gaps
    var sampleBufferSize = 16384;

    // initialization flags
    var emscriptenInitialized = false;

    // our local instance of window.AudioContext
    var audioCtx;

    // consumer callbacks. these fire once QRTone is ready to create transmitter/receiver
    var readyCallbacks = [];
    var readyErrbacks = [];
    var failReason = "";

    // isReady tells us if we can start creating transmitters and receivers
    // we need the emscripten portion to be running and we need our
    // async fetch of the profiles to be completed
    function isReady() {
        return emscriptenInitialized;
    };

    function isFailed() {
        return failReason !== "";
    };

    // start gets our AudioContext and notifies consumers that QRTone can be used
    function start() {
        var len = readyCallbacks.length;
        for (var i = 0; i < len; i++) {
            readyCallbacks[i]();
        }
    };

    function initAudioContext() {
        if (audioCtx === undefined) {
            audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            console.log(audioCtx.sampleRate);
        }
    };

    function fail(reason) {
        failReason = reason;
        var len = readyErrbacks.length;
        for (var i = 0; i < len; i++) {
            readyErrbacks[i](reason);
        }
    };

    function checkInitState() {
        if (isReady()) {
            start();
        }
    };

    // this is intended to be called only by emscripten
    function onEmscriptenInitialized() {
        emscriptenInitialized = true;
        checkInitState();
    };

    /**
     * Add a callback to be called when QRTone is ready for use, e.g. when transmitters and receivers can be created.
     * @function addReadyCallback
     * @memberof QRTone
     * @param {function} c - The user function which will be called
     * @param {onError} [onError] - User errback function
     * @example
     * addReadyCallback(function() { console.log("ready!"); });
     */
    function addReadyCallback(c, errback) {
        if (isReady()) {
            c();
            return;
        }
        readyCallbacks.push(c);
        if (errback !== undefined) {
            if (isFailed()) {
                errback(failReason);
                return;
            }
            readyErrbacks.push(errback);
        }
    };

    // receiver functions

    function audioInputReady() {
        var len = audioInputReadyCallbacks.length;
        for (var i = 0; i < len; i++) {
            audioInputReadyCallbacks[i]();
        }
    };

    function audioInputFailed(reason) {
        audioInputFailedReason = reason;
        var len = audioInputFailedCallbacks.length;
        for (var i = 0; i < len; i++) {
            audioInputFailedCallbacks[i](audioInputFailedReason);
        }
    };

    function addAudioInputReadyCallback(c, errback) {
        if (errback !== undefined) {
            if (audioInputFailedReason !== "") {
                errback(audioInputFailedReason);
                return
            }
            audioInputFailedCallbacks.push(errback);
        }
        if (audioInput instanceof MediaStreamAudioSourceNode) {
            c();
            return
        }
        audioInputReadyCallbacks.push(c);
    }

    function gUMConstraints() {
        if (navigator.webkitGetUserMedia !== undefined) {
            return {
                audio: {
                    optional: [
                      {googAutoGainControl: false},
                      {googAutoGainControl2: false},
                      {echoCancellation: false},
                      {googEchoCancellation: false},
                      {googEchoCancellation2: false},
                      {googDAEchoCancellation: false},
                      {googNoiseSuppression: false},
                      {googNoiseSuppression2: false},
                      {googHighpassFilter: false},
                      {googTypingNoiseDetection: false},
                      {googAudioMirroring: false},
                      {autoGainControl: false},
                      {noiseSuppression: false}
                    ]
                }
            };
        }
        if (navigator.mozGetUserMedia !== undefined) {
            return {
                audio: {
                    echoCancellation: false,
                    mozAutoGainControl: false,
                    mozNoiseSuppression: false,
                    autoGainControl: false,
                    noiseSuppression: false
                }
            };

        }
        return {
            audio: {
                echoCancellation: false,
                optional: [
                  {autoGainControl: false},
                  {noiseSuppression: false}
                ]
            }
        };
    };


    function createAudioInput() {
        audioInput = 0; // prevent others from trying to create
        window.setTimeout(function() {
            gUM.call(navigator, gUMConstraints(),
                function(e) {
                    audioInput = audioCtx.createMediaStreamSource(e);

                    // stash a very permanent reference so this isn't collected
                    window.qrtone_receiver_anti_gc = audioInput;

                    audioInputReady();
                }, function(reason) {
                    audioInputFailed(reason.name);
                });
        }, 0);
    };

    /**
     * Disconnect QRTone.js from its microphone source
     * This will disconnect QRTone.js's microphone fully from all receivers
     * This is useful to cause the browser to stop displaying the microphone icon
     * Browser support is limited for disconnecting a single destination, so this
     * call will disconnect all receivers.
     * It is highly recommended to call this only after destroying any receivers.
     * @function disconnect
     */
    function disconnect() {
        if (audioInput !== undefined) {
            audioInput.disconnect();
            audioInput = undefined;
            delete window.qrtone_receiver_anti_gc;
        }
    };
    function transmitter(opts) {
        initAudioContext();
        var done = opts.onFinish;

        var encoder = _qrtone_new();
        _qrtone_init(encoder, audioCtx.sampleRate);

        var frame_len = 0;

        var samples = _malloc(4 * sampleBufferSize);

        // yes, this is pointer arithmetic, in javascript :)
        var sample_view = Module.HEAPF32.subarray((samples/4), (samples/4) + sampleBufferSize);

        var dummy_osc;

        // we'll start and stop transmitter as needed
        //   if we have something to send, start it
        //   if we are done talking, stop it
        var running = false;
        var transmitter;

        // prevent races with callbacks on destroyed in-flight objects
        var destroyed = false;

        var onaudioprocess = function(e) {
            var output_l = e.outputBuffer.getChannelData(0);

            if (played === true) {
                // we've already played what's in sample_view, and it hasn't been
                //   rewritten for whatever reason, so just play out silence
                for (var i = 0; i < sampleBufferSize; i++) {
                    output_l[i] = 0;
                }
                return;
            }

            played = true;

            output_l.set(sample_view);
            window.setTimeout(writebuf, 0);
        };

        var startTransmitter = function () {
            if (destroyed) {
                return;
            }
            if (transmitter === undefined) {
                // we have to start transmitter here because mobile safari wants it to be in response to a
                // user action
                var script_processor = (audioCtx.createScriptProcessor || audioCtx.createJavaScriptNode);
                // we want a single input because some implementations will not run a node without some kind of source
                // we want two outputs so that we can explicitly silence the right channel and no mixing will occur
                transmitter = script_processor.call(audioCtx, sampleBufferSize, 1, 2);
                transmitter.onaudioprocess = onaudioprocess;
                // put an input node on the graph. some browsers require this to run our script processor
                // this oscillator will not actually be used in any way
                dummy_osc = audioCtx.createOscillator();
                dummy_osc.type = 'square';
                dummy_osc.frequency.value = 420;

            }
            dummy_osc.connect(transmitter);
            transmitter.connect(audioCtx.destination);
            running = true;
        };

        var stopTransmitter = function () {
            if (destroyed) {
                return;
            }
            dummy_osc.disconnect();
            transmitter.disconnect();
            running = false;
        };

        // we are only going to keep one chunk of samples around
        // ideally there will be a 1:1 sequence between writebuf and onaudioprocess
        // but just in case one gets ahead of the other, this flag will prevent us
        // from throwing away a buffer or playing a buffer twice
        var played = true;

        // unfortunately, we need to flush out the browser's sound sample buffer ourselves
        // the way we do this is by writing empty blocks once we're done and *then* we can disconnect
        var empties_written = 0;

        var written = 0;

        // measure some stats about encoding time for user
        var last_emit_times = [];
        var num_emit_times = 3;

        // writebuf calls _send and _emit on the encoder
        // first we push as much payload as will fit into encoder's tx queue
        // then we create the next sample block (if played = true)
        var writebuf = function() {
            if (destroyed) {
                return;
            }

            if (running === false) {
                startTransmitter();
            }

            // now set the sample block
            if (played === false) {
                // the existing sample block has yet to be played
                // we are done
                return;
            }

            // reset signal buffer
            _memset(samples, 0, sampleBufferSize * 4);
            _qrtone_get_samples(encoder, samples, sampleBufferSize, 1.0);
            written += sampleBufferSize;

            // frame_len is the total number of audio samples of the payload
            // So if written >= number of samples. The output is done
            if (written >= frame_len + sampleBufferSize) {
                if (empties_written < 3) {
                    // flush out browser's sound sample buffer before quitting
                    for (var i = 0; i < sampleBufferSize; i++) {
                        sample_view[i] = 0;
                    }
                    empties_written++;
                    played = false;
                    return;
                }
                // looks like we are done
                // user callback
                if (done !== undefined) {
                        done();
                }
                if (running === true) {
                    stopTransmitter();
                }
                return;
            }

            played = false;
            empties_written = 0;


        };

        var transmit = function(buf, qLevel, addCRC) {
            if (destroyed) {
                return;
            }
            if(!Array.isArray(buf)) {
                console.error("transmit expect Array of integer as first parameter")
                return;
            }
            if(qLevel === undefined) {
                qLevel = 2;
            } else {
                qLevel = Math.min(3, Math.max(0, qLevel));
            }

            if(addCRC === undefined) {
                addCRC = 1;
            }

            var contenti8 = _malloc(buf.length);
            writeArrayToMemory(new Uint8Array(buf), contenti8);
            frame_len = _qrtone_set_payload_ext(encoder, contenti8, buf.length, qLevel, addCRC);

            _free(contenti8);

            // now do an update. this may or may not write samples
            writebuf();
        };

        var destroy = function() {
            if (destroyed) {
                return;
            }

            if (running === true) {
                stopTransmitter();
            }

            _free(samples);
            _qrtone_free(encoder);
            _free(encoder);

            destroyed = true;
        };

        return {
            transmit: transmit,
            destroy: destroy,
            frameLength: frame_len
        };
    };

    return {
        emscriptenInitialized: onEmscriptenInitialized,
        addReadyCallback: addReadyCallback,
        transmitter: transmitter,
        disconnect: disconnect
    };
})();


Module['onRuntimeInitialized'] = QRTone.emscriptenInitialized;

