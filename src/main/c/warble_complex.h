#ifndef H_WARBLE_COMPLEX
#define H_WARBLE_COMPLEX

#include "math.h"

typedef struct _warblecomplex
{
	double r;
	double i;
} warblecomplex;

#define NEW_CX(R,I) (warblecomplex) { R, I }
#define CX_ADD(c1,c2) NEW_CX(c1.r + c2.r, c1.i + c2.i)
#define CX_SUB(c1,c2) NEW_CX(c1.r - c2.r, c1.i - c2.i)
#define CX_MUL(c1,c2) NEW_CX(c1.r * c2.r - c1.i * c2.i, c1.r * c2.i + c1.i * c2.r)
#define CX_EXP(c1) NEW_CX(cos(c1.r), -sin(c1.r))

#endif