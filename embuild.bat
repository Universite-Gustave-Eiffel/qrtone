emcc libwarble/src/warble.c libwarble/src/warble_complex.c libcorrect/src/reed-solomon/encode.c ^
libcorrect/src/reed-solomon/decode.c libcorrect/src/reed-solomon/polynomial.c ^
libcorrect/src/reed-solomon/reed-solomon.c -Ilibcorrect/include -Ilibwarble/include -o function.html ^
-s "EXPORTED_FUNCTIONS=['_warble_create', '_warble_init', '_warble_feed', '_warble_feed_window_size']" ^
-s "EXTRA_EXPORTED_RUNTIME_METHODS=['ccall', 'cwrap']"