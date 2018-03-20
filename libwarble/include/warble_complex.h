#ifndef H_WARBLE_COMPLEX
#define H_WARBLE_COMPLEX

#include "math.h"

typedef struct _warblecomplex
{
	double r;
	double i;
} warblecomplex;



struct _warblecomplex NEW_CX(double r, double i);

struct _warblecomplex CX_ADD(const warblecomplex c1, const warblecomplex c2);

struct _warblecomplex CX_SUB(const warblecomplex c1, const warblecomplex c2);

struct _warblecomplex CX_MUL(const warblecomplex c1, const warblecomplex c2);

struct _warblecomplex CX_EXP(const warblecomplex c1);


#ifdef max
#undef max
#endif
#ifdef min
#undef min
#endif
#define max(a, b) ((a) > (b) ? (a) : (b))
#define min(a, b) ((a) < (b) ? (a) : (b))

#ifndef M_PI
  #define M_PI 3.1415926535897932385
#endif

#endif