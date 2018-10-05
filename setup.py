import os
from setuptools import setup, Extension

NAME = "pywarble"
VERSION = "0.1"
DESCR = "Encapsulate/Decode data into audio feed"
URL = "https://github.com/Ifsttar/openwarble"
REQUIRES = []

AUTHOR = "Nicolas Fortin"
EMAIL = "nicolas.fortin -at- ifsttar.fr"

LICENSE = "BSD3"

SRC_DIR = "pywarble"
PACKAGES = [SRC_DIR]

try:
    import Cython
    USE_CYTHON = True
except ImportError as e:
    USE_CYTHON = False

ext = ".pyx" if USE_CYTHON else ".c"

ext_1 = Extension(SRC_DIR + ".wrapped",
                  ["libwarble/src/warble.c","libwarble/src/warble_complex.c",
                   "libcorrect/src/reed-solomon/reed-solomon.c",
                   "libcorrect/src/reed-solomon/encode.c",
                   "libcorrect/src/reed-solomon/decode.c",
                   "libcorrect/src/reed-solomon/polynomial.c",SRC_DIR + "/pywarble" + ext],
                  libraries=[],
                  extra_compile_args=['-std=c99'],
                  include_dirs=["libwarble/include", "libcorrect/include"])

EXTENSIONS = [ext_1]

if USE_CYTHON:
    from Cython.Build import cythonize
    EXTENSIONS = cythonize(EXTENSIONS)

if __name__ == "__main__":
    setup(install_requires=REQUIRES,
          packages=PACKAGES,
          zip_safe=False,
          name=NAME,
          version=VERSION,
          description=DESCR,
          author=AUTHOR,
          author_email=EMAIL,
          url=URL,
          license=LICENSE,
          test_suite="tests",
          ext_modules=EXTENSIONS
          )
