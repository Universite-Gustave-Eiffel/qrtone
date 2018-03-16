#include "warble_complex.h"

struct _warblecomplex NEW_CX(double r, double i) {
	return (warblecomplex) { r, i };
}

struct _warblecomplex CX_ADD(const warblecomplex c1, const warblecomplex c2) {
	return (warblecomplex) {c1.r + c2.r, c1.i + c2.i};
}

struct _warblecomplex CX_SUB(const warblecomplex c1, const warblecomplex c2) {
	return (warblecomplex) { c1.r - c2.r, c1.i - c2.i };
}

struct _warblecomplex CX_MUL(const warblecomplex c1, const warblecomplex c2) {
	return (warblecomplex) { c1.r * c2.r - c1.i * c2.i, c1.r * c2.i + c1.i * c2.r };
}

struct _warblecomplex CX_EXP(const warblecomplex c1) {
	return (warblecomplex) { cos(c1.r), -sin(c1.r) };
}
