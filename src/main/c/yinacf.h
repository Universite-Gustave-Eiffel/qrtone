#ifndef YINACF_H
#define YINACF_H
/* Copyight (c) 1005 Michaël Roy
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation; either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/
/** 
*   @file yinacf.h
*
*   Yin algorithm implementation template.
*
*   The Yin algorithm estimates the fundamental frequency of a signal using
*   autocorrellation.
*
*   @ingroup DSPALG
*   @author Michael Roy <a href="mailto:tika1966@yahoo.com">tika1966@yahoo.com</a>
*   @version 1.0
*
*/

// undef to remove Intel EMM support
#define YIN_EMMSUPPORT
#ifdef YIN_EMMSUPPORT
# include <emmintrin.h>
#endif

/**
*   the Yin algorithm.
*
*   Complete Yin algorithm as described by A. de Cheveigne and H. Kawahara in
*   <a href="http://recherche.ircam.fr/equipes/pcm/cheveign/ps/2002_JASA_YIN_proof.pdf">
*   YIN, a fundamental frequency estimator for speech and music</a>, 
*   J. Acoust. Soc. Am. 111, 1917-1930.
*
*   According to the author, this algorithm works best with low-pass 
*   prefiltering at 1000 Hz, although it will function really well without.
*
*   This particular implementation will function in real time with sample rates of 
*   up to 20 kHz
*
*/

template <class Sample>
class YinACF {
private:

    /**
    *   holds solution for each sample.
    *
    *   A solution is computed for each sample passed to YinACF::tick().
    *   These solutions are held in a buffer of length YinACF::TMAX for 
    *   frequency smoothing.
    *
    *   @see class YinACF
    */

    class Solution {
    public:
        Sample* diff;           /**< difference (step 2) */
        Sample* cmndiff;        /**< cumulative mean normalized difference (step 3) */
        unsigned est;           /**< T[theta] */
        Sample freq;            /**< frequency estimate */

    public:

        /**
        *   builds empty solution.
        *
        *   Builds empty solution.
        */

        Solution() {
            diff = 0;
            cmndiff = 0;
            est = 0;
            freq = 0;
        }

        /**
        *   builds solution with window size \a W.
        *
        *   Builds solution with window size \a W.
        *
        *   @param W window size in samples.
        */

        Solution(unsigned W) {
            diff = new Sample[W];
            cmndiff = new Sample[W];
            est = 0;
            freq = 0;
        }

        /**
        *   destructor.
        *
        *   Frees all allocated memory.
        */

        ~Solution() {
            if (diff) delete [] diff;
            if (cmndiff) delete [] cmndiff;
        }
    };

private:
    Sample* in;             /**< input delay line */
    unsigned head;          /**< delay line pointer */
    unsigned N;             /**< delay line length */
    unsigned W;             /**< window length */
    Sample THRESHOLD;       /**< threshold for initial frequency estimate (step 4) */
    unsigned TMAX;          /**< TMAX for frequency results integration */
    Solution* sol;          /**< solutions buffer */
    unsigned ho;            /**< solution pointer */

public:
    /**
    *   builds an uninitialized YinACF object.
    *
    *   Builds an uninitialized YinACF object.  The object must be initialized
    *   by a call to build() before use.
    *
    *   @see build()
    */

    YinACF() {
        THRESHOLD = Sample(1.e-1);
        in = 0;
        head = 0;
        N = 0;
        W = 0;
        TMAX = 0;
        ho = 0;
        sol = 0;
    }

    /**
    *   destructor.
    *
    *   Destroys the YinACF object.
    */

    ~YinACF() {
        destroy();
    }

    /**
    *   initializes the object before use.
    *
    *   Initializes the YinACF object for use.
    *
    *   @param windowSize   size of the autocorrelation window in samples. The 
    *                       window size determinates the minimum frequency that 
    *                       can be detected by the algorithm.  This minimum 
    *                       frequency is calculated by the equation 
    *                       \f$f_{min}=\frac{f_s}{W}\f$,
    *                       where \f$W\f$ is the window size, and \f$f_s\f$ is 
    *                       the sampling rate.
    *   @param tmax         \f$T_{max}\f$ in samples, as described in paragraph 
    *                       2.F of the original paper.
    *   @returns always \p true.
    */

    bool build (unsigned windowSize, unsigned tmax) {

        destroy();

        W = windowSize;
        N = (2 * W);
        TMAX = tmax | 1;

        head = 0;
        in = new Sample[N];

        ho = 0;
        sol = new Solution[TMAX];
        
        for (unsigned i = 0; i < TMAX; ++i)
            sol[i].Solution::Solution(W);

        reset();
        return true;
    }
    
    /**
    *   frees memory.
    *
    *   Frees the memory allocated by build().
    *
    *   @see build()
    */

    void destroy() {
        N = 0;
        W = 0;
        TMAX = 0;

        if (in) delete [] in;
        if (sol) delete[] sol;
        head = 0;
        in = 0;

        ho = 0;
        sol = 0;
    }

    /**
    *   clears buffers.
    *
    *   Clears all buffers to \c 0.
    */

    void reset() {
        unsigned i, j;
        if (N) {
            head = 0;
            for (i = 0; i < N; ++i)
                in[i] = 0;

            ho = 0;
            for (i = 0; i < TMAX; ++i) {
                sol[i].est = 0;
                sol[i].freq = 0;
                sol[i].diff[0] = 0;
                sol[i].cmndiff[0] = 1;
                for (j = 1; j < W; ++j) {
                    sol[i].diff[j] = 0;
                    sol[i].cmndiff[j] = 0;
                }
            }
        }
    }

    /**
    *   processes sample.
    *
    *   Processes signal data.
    *
    *   @param s    signal sample.
    *   @returns    frequency of the signal in normalized frequency range 
    *               [0.. 0.5].  This frequency reading is delayed by the 
    *               algorithm's latency.
    *
    *   @see getLatency()
    */

    virtual Sample tick(const Sample& s) {
        Sample freq = 0;
        pushSolution();
        Solution* psin = getSolution(TMAX - 1);
        Solution* psprev = getSolution(TMAX - 2);

        psin->est = 0;
        psin->freq = 0;

        computeDiff(s, psin, psprev);  // step 2,3

        //  make preliminary guesstimate
        estimateFreq(psin, 0, W);

        // return best local frequency estimate (is delayed by TMAX /2)
        return getBestLocalEstimate();
    }

    /**
    *   returns algorithm latency.
    *
    *   Returns the latency of the algorithm in samples.
    *
    *   @see tick()
    */

    inline unsigned getLatency() const {
        return N + (TMAX / 2);
    }

    /**
    *   returns window size.
    *
    *   Returns the window size in samples.
    *
    *   @see build()
    */

    inline unsigned getWindowSize() const {
        return W;
    }

    /**
    *   returns a solution's difference function.
    *
    *   Returns a solution's difference function.  This function is provided 
    *   for debugging and visualiation of the difference function at a point 
    *   in time.
    *
    *   @param offset time offset in samples, can vary between \p 0, for the 
    *          last (current) sample fed to the algorithm to \p TMAX.
    *
    *   @returns the requested solution's difference function.
    */

    inline const Sample* getDiff(int offset = 0) const {
        return getSolution(offset)->diff;
    }

    /**
    *   returns a solution's cumulative mean difference function.
    *
    *   Returns a solution's cumulative mean difference function.  This 
    *   function is provided for debugging and visualiation of the cumulative 
    *   mean difference function at a point in time.
    *
    *   @param offset time offset in samples, can vary between \p 0, for the 
    *          last (current) sample fed to the algorithm to \p TMAX.
    *
    *   @returns the requested solution's cumulative mean difference function.
    */

    inline const Sample* getCMNDiff(int offset = 0) const {
        return getSolution(offset)->cmndiff;
    }

    /**
    *   returns a solution's calculated frequency.
    *
    *   Returns a solution's calculated frequency.  This function is provided 
    *   for debugging and visualiation of the calculated frequency at a point 
    *   in time.
    *
    *   @param offset time offset in samples, can vary between \p 0, for the 
    *          last (current) sample fed to the algorithm to \p TMAX.
    *
    *   @returns the requested solution's calculated frequency.
    */

    inline const getFrequency(int offset = 0) const {
        return getSolution(offset)->freq;
    }

    /**
    *   returns the treshold used by getDip().
    *
    *   The threshold used by getDip(), its default value is \p 0.1.
    *
    *   @returns the treshold used by getDip().
    */

    inline Sample getThreshold() const {
        return THRESHOLD;
    }

    /**
    *   changes the treshold used by getDip().
    *
    *   Modigfies the threshold used by getDip(), its default value is \p 0.1.
    */

    inline void setThreshold(const Sample& threshold) {
        THRESHOLD = threshold;
    }

    /**
    *   returns TMAX.
    *
    *   Returns TMAX.
    *
    *   @returns TMAX.
    */

    inline unsigned getMaxPeriod() const {
        return TMAX;
    }

private:
    inline void pushInput(const Sample& s) {
        in[head++] = s;
        if (head >= N) head = 0;
    }

    inline void pushSolution() {
        if (++ho >= (int)TMAX) ho = 0;
    }

    const Sample& getSample(unsigned delay) const {
        unsigned n = head + delay;
        if (n >= N) n -= N;
        return in[n];
    }

    const void getSamples(unsigned delay, unsigned n, Sample* p) const {
        unsigned o = head + delay;
        while(n--) {
            if (o >= N) o -= N;
            *(p++) = in[o++];
        }
    }

    Solution* getSolution(unsigned n) const {
        n = n + ho;
        if (n >= TMAX) n -= TMAX;
        return (Solution*)&sol[n];
    }

    virtual void computeDiff(const Sample& s, Solution* pscur, Solution* psprev) {
        // call appropriate optimized template
        computeDiff(this, s, pscur, psprev);
    }

    virtual void estimateFreq(Solution* ps, unsigned mn, unsigned mx) {
        //  make preliminary guesstimate
        unsigned est = getDip(ps, mn, mx);       // step 4
        if (est) ps->est = est;
    }

    virtual unsigned getDip(Solution* ps, int mn, int mx) const {
        static const int MIN_WL = 2;
        int i;
        Sample *cmndiff = ps->cmndiff;
        Sample mindip = Sample(10000);
        unsigned mini = 0;
        bool lt1, lt2;

        if (mn < MIN_WL) mn = MIN_WL;
        if (mx >= (int)W) mx = (int)(W - 1);
        // return global minima or first minima below threshold
        lt2 = (cmndiff[mn - 1] < cmndiff[mn]);
        for (i = mn; i <= mx; ++i) {
            lt1 = lt2;
            lt2 = (cmndiff[i] < cmndiff[i + 1]);
            if (!lt1 && lt2) {
                if (cmndiff[i] < mindip) {
                    mindip = cmndiff[i];
                    mini = i;
                    if (mindip < THRESHOLD)
                        break;
                }
            }
        }
        return mini;
    }

    virtual Sample parabolicInterpolation(unsigned est, Solution* ps) {
        //  parabolic interpolation with b-a = 1, b-c = -1, fb < fa && fb < fc
        //  returns normalized frequency 
        //  
        Sample d1, d2;
        unsigned i = 0;
        Sample* p = &ps->diff[est];

        //  dip index could be slightly offset from cmndiff[]'s
        while(1) {
            if (*(p - 1) < *p) {
                --est;
                --p;
            }
            else if (*(p + 1) <= *p) {
                ++est;
                ++p;
            }
            else
                break;
            if (++i > 4 || est >= W - 1)
                return 0;
        }

        //  xmin = b - (1/2)[(((b-a)^2*(fb-fc))-((b-c)^2*(fb-fa))) / (((b-a)(fb-fc)-(b-c)(fb-fa)))]
        //  xmin = b - (1/2)[((fb-fc)-(fb-fa))/((fb-fc)+(fb-fa))]
        //  let d1 = fb-fa, d2 = fb-fc
        //  xmin = b-((d2-d1)/(2(d2+d1)))

        d1 = *p - *(p - 1);
        d2 = *p - *(p + 1);
        Sample b = Sample(est);
        Sample num = (d2 - d1);
        Sample div = (d2 + d1);
        if (div == 0)
            return Sample(1) / b;
        return Sample(1) / (b - (num / (2 * div)));
    }

    virtual Sample getBestLocalEstimate() {
        Solution* pscur = getSolution(TMAX / 2);
        Sample dmin = pscur->cmndiff[pscur->est];
        Solution* psmin = pscur;

        for (unsigned i = 0; i < TMAX; ++i) {
            Solution* ps = getSolution(i);
            if (ps->est && ps->cmndiff[ps->est] < dmin) {
                dmin = ps->cmndiff[ps->est];
                psmin = ps;
            }
        }

        if (psmin != pscur) {
            //  re-estimate around local minima
            unsigned est = getDip(pscur, (int)psmin->est - TMAX / 5, (int)psmin->est + TMAX / 5);
            if (est && (!pscur->est || pscur->cmndiff[est] < pscur->cmndiff[pscur->est])) {
                pscur->est = est;
            }
        }

        if (pscur->est)
            pscur->freq = parabolicInterpolation(pscur->est, pscur);
        else 
            pscur->freq = 0;
        return pscur->freq;
    }

    template<class T>
    inline static void computeDiff(YinACF<T>* that, const Sample& s, typename YinACF<T>::Solution* pscur, typename YinACF<T>::Solution* psprev) {
        Sample sum = 1;
        Sample ii = 1;
        Sample sm1 = that->getSample(0);
        that->pushInput(s);
        Sample sw = that->getSample(that->W - 1);
        Sample* d = pscur->diff;
        Sample* p = psprev->diff;
        Sample* c = pscur->cmndiff;
        for (unsigned i = 1; i < that->W; ++i) {
            Sample d2 = sw - that->getSample(i + that->W - 1);
            Sample d1 = sm1 - that->getSample(i - 1);
            d[i] = p[i] + ((d2 + d1) * (d2 - d1));     //  a2 - b2 = (a + b)(a - b)
            sum += d[i];
            c[i] = ii * (d[i] / sum);
            ii += Sample(1);
        }
    }

#ifdef YIN_EMMSUPPORT
#pragma optimize("s", on)
    const void getSamples(unsigned delay, __m128& f) const {
        float p[4];
        unsigned n = head + delay;
        if (n >= N) n -= N;
        int x = (int)N - (int)(n + 4);
        if (x >= 0)
            f = _mm_loadu_ps(&in[n]);
        else {
            if (x == -1) {
                *(long*)&p[0] = *(long*)&in[N-3];
                *(long*)&p[1] = *(long*)&in[N-2];
                *(long*)&p[2] = *(long*)&in[N-1];
                *(long*)&p[3] = *(long*)&in[0];
            }
            else if (x == -2) {
                *(long*)&p[0] = *(long*)&in[N-2];
                *(long*)&p[1] = *(long*)&in[N-1];
                *(long*)&p[2] = *(long*)&in[0];
                *(long*)&p[3] = *(long*)&in[1];
            }
            else {
                *(long*)&p[0] = *(long*)&in[N-1];
                *(long*)&p[1] = *(long*)&in[0];
                *(long*)&p[2] = *(long*)&in[1];
                *(long*)&p[3] = *(long*)&in[2];
            }
            f = _mm_loadu_ps(p);
        }
    }

    inline static void computeDiff(YinACF<Sample>* that, const float& s, typename YinACF<Sample>::Solution* pscur, typename YinACF<Sample>::Solution* psprev) {
        static const __m128 ZERO = _mm_set_ps1(0.f);
        static const __m128 ONE = _mm_set_ps(1.f, 2.f, 3.f, 4.f);
        static const __m128 FOUR = _mm_set_ps1(4.f);
        __m128 sums = _mm_set_ps1(1.f), t;
        __m128 ii = ONE;
        __m128 sm1 = _mm_set_ps1(that->getSample(0));
        that->pushInput(s);
        __m128 sw = _mm_set_ps1(that->getSample(that->W - 1));

        float* d = pscur->diff;
        float* p = psprev->diff;
        float* c = pscur->cmndiff;

        unsigned i;
        for (i = 1; i < that->W - 4; i += 4) {
            __m128 smp1, smp2;
            that->getSamples(i + that->W - 1, smp1);
            that->getSamples(i - 1, smp2);

            __m128 d2 = _mm_sub_ps(sw, smp1);
            __m128 d1 = _mm_sub_ps(sm1, smp2);
            __m128 di = _mm_add_ps(_mm_loadu_ps(p + i), _mm_mul_ps(_mm_add_ps(d2, d1), _mm_sub_ps(d2, d1)));
            _mm_storeu_ps(d + i, di);

            sums = _mm_shuffle_ps(sums, sums, _MM_SHUFFLE(3,3,3,3));
            sums = _mm_add_ps(sums, di);
            sums = _mm_add_ps(sums, _mm_movelh_ps(ZERO, di));
            t = _mm_shuffle_ps(di, di, _MM_SHUFFLE(2,1,0,3));
            t = _mm_move_ss(t, ZERO);
            sums = _mm_add_ps(sums, t);
            sums = _mm_add_ps(sums, _mm_movelh_ps(ZERO, t));

            _mm_storeu_ps(c + i, _mm_mul_ps(ii, _mm_div_ps(di, sums)));
            ii = _mm_add_ps(ii, FOUR);
        }
    }
#endif
};

#endif // YINACF_H