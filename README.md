# openwarble
Convert data to/from audio signals

This library is based on Yin algorithm implementation by Michael Roy.

Yin algorithm.

Complete Yin algorithm as described by A. de Cheveigne and H. Kawahara in
<a href="http://recherche.ircam.fr/equipes/pcm/cheveign/ps/2002_JASA_YIN_proof.pdf">
YIN, a fundamental frequency estimator for speech and music</a>, 
J. Acoust. Soc. Am. 111, 1917-1930.

According to the author, this algorithm works best with low-pass 
prefiltering at 1000 Hz, although it will function really well without.

This particular implementation will function in real time with sample rates of 
up to 20 kHz

The library is converted into a native Android library thanks to renjin GCC-Bridge (https://github.com/bedatadriven/renjin/tree/master/tools/gcc-bridge)