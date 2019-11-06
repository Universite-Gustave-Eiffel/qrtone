# OpenWarble
<img src="./icon.svg" width="10%" height="10%">

[![Build Status](https://travis-ci.org/Ifsttar/openwarble.svg?branch=master)](https://travis-ci.org/Ifsttar/openwarble)

Send and receive data using only speaker and microphone.

This library is written in java under BSD 3 license.

Signal processing use method *Sysel and Rajmic:Goertzel algorithm generalized to non-integer multiples of fundamental frequency. EURASIP Journal on Advances in Signal Processing 2012 2012:56.*

# How it works ?

The data is converted into a sequence of pure sine waves.
 
The first and second pitch are used to recognize and trigger the sequence of tones.

If the receiver misses a frequency due to loud background noise or reverberation, the data is reconstructed using a forward error correction algorithm. The fec is a RS(10, 4) with 1 byte of CRC, than mean for each 12 bytes, the code can correct up to 2 erroneous bytes.
 
In addition, a single sequence of locations can contain multiple interleaved payloads and correction codes. Interleaving spreads an error to other correction codes, so the correction is much more robust.

Here a spectrogram of a sequence:

![OpenWarble spectrogram](noise.png)

*Top recorded audio in real situation, bottom source signal.*

This library can be included in android app with Api 14+ (Android 4.0.2) and does not require dependencies, the jar size is only 17 kbytes !
