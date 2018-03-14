# openwarble
Convert data to/from audio signals

This library is written in C99 under BSD 3 license.

This library use the library [libcorrect](https://github.com/quiet/libcorrect) for forward error correction.

Signal processing use method *Sysel and Rajmic:Goertzel algorithm generalized to non-integer multiples of fundamental frequency. EURASIP Journal on Advances in Signal Processing 2012 2012:56.*

The library is converted into a native Android library thanks to renjin GCC-Bridge (https://github.com/bedatadriven/renjin/tree/master/tools/gcc-bridge)