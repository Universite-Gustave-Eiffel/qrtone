# file: yinacf.pxd
# doc from http://cython.readthedocs.io/en/latest/src/userguide/wrapping_CPlusPlus.html


cdef extern from "src/main/c/yinacf.h":
    cdef cppclass YinACF:
        YinACF() except +
        bool build (unsigned windowSize, unsigned tmax)
        void destroy()
        void reset()
        virtual Sample tick(const Sample& s)
        inline unsigned getLatency() const
        inline unsigned getWindowSize() const
        inline const Sample* getDiff(int offset = 0) const
        inline const Sample* getCMNDiff(int offset = 0) const
        inline const getFrequency(int offset = 0) const
        inline Sample getThreshold() const
        inline void setThreshold(const Sample& threshold)
        inline unsigned getMaxPeriod() const
        
cdef class PyYinACF:
    cdef YinACF c_yinacf      # hold a C++ instance which we're wrapping
    def __cinit__(self):
        self.c_yinacf = YinACF()
    def build(self):
        return self.c_yinacf.build()
    def destroy(self):
        return self.c_yinacf.destroy()
    def reset(self):
        return self.c_yinacf.reset()
    def reset(tick, samples):
        return self.c_yinacf.tick(samples)
    def get_latency(self):
        return self.c_yinacf.getLatency()
    def get_window_size(self):
        return self.c_yinacf.getWindowSize()
    def get_diff(self, offset):
        return self.c_yinacf.getDiff(offset)
    def get_cmn_diff(self, offset):
        return self.c_yinacf.getCMNDiff(offset)
    def get_frequency(self, offset):
        return self.c_yinacf.get_frequency(offset)
    def get_threshold(self, offset):
        return self.c_yinacf.get_threshold(offset)