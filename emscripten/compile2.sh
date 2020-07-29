emcc ../src/qrtone.c ../src/reed_solomon.c -I../src -o qrtone_emscripten.js -s "EXPORTED_FUNCTIONS=['_qrtone_new', '_qrtone_init', '_qrtone_free', '_qrtone_get_maximum_length', '_qrtone_set_payload', '_qrtone_set_payload_ext', '_qrtone_get_samples', '_memset']" -s "EXTRA_EXPORTED_RUNTIME_METHODS=['ccall', 'cwrap','getValue','ALLOC_NORMAL']" --pre-js qrtone.js -s ENVIRONMENT=web




