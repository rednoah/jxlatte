package com.traneptora.jxlatte.util;

import java.util.Arrays;

public final class MathHelper {

    public static final float SQRT_2 = (float)StrictMath.sqrt(2.0D);
    public static final float SQRT_H = (float)StrictMath.sqrt(0.5D);
    public static final float SQRT_F = (float)StrictMath.sqrt(0.125D);
    public static final float PHI_BAR = (float)((StrictMath.sqrt(5D) * 0.5D) - 0.5D);
    public static final float PHI = (float)((StrictMath.sqrt(5D) * 0.5D) + 0.5D);

    private static final Point ZERO = new Point();

    // s, n, k
    private static float[][][] cosineLut = new float[9][][];

    static {
        final double root2 = StrictMath.sqrt(2.0D);
        for (int l = 0; l < cosineLut.length; l++) {
            int s = 1 << l;
            cosineLut[l] = new float[s - 1][s];
            for (int n = 0; n < cosineLut[l].length; n++) {
                for (int k = 0; k < cosineLut[l][n].length; k++) {
                    cosineLut[l][n][k] = (float)(root2 * StrictMath.cos(Math.PI * (n + 1) * (k + 0.5D) / s));
                }
            }
        }
    }

    public static int unpackSigned(int value) {
        return (value & 1) == 0 ? (value >>> 1) : (~value >>> 1) | 0x80_00_00_00;
    }

    public static int round(float d) {
        return (int)(d + 0.5f);
    }

    public static float erf(float z) {
        final float az = Math.abs(z);
        float absErf;
        // first method is more accurate for most z, but becomes inaccurate for very small z
        // so we fall back on the second method
        if (az > 1e-4f) {
            /*
             * William H. Press, Saul A. Teukolsky, William T. Vetterling, and Brian P. Flannery. 1992.
             * Numerical recipes in C (2nd ed.): the art of scientific computing. Cambridge University Press, USA.
             */
            final float t = 1.0f / (az * 0.5f + 1.0f);
            final float u = t * (t * (t * (t * (t * (t * (t * (t * (t * 0.17087277f - 0.82215223f) + 1.48851587f) - 1.13520398f)
                              + 0.27886807f) - 0.18628806f) + 0.09678418f) + 0.37409196f) + 1.00002368f) - 1.26551223f;
            absErf = 1.0f - t * (float)Math.exp(-z * z + u);
        } else {
            /*
             * Milton Abramowitz and Irene A. Stegun. 1964. Handbook of Mathematical Functions with formulas,
             * graphs, and mathematical tables, fover Publications, USA.
             */
            final float t = 1.0f / (az * 0.47047f + 1.0f);
            final float u = t * (t * (t * 0.7478556f - 0.0958798f) + 0.3480242f);
            absErf = 1.0f - u * (float)Math.exp(-z * z);
        }
        if (z < 0)
            return -absErf;
        return absErf;
    }

    public static void inverseDCTHorizontal(float[] src, float[] dest,
            int xStartIn, int xStartOut, int xLogLength, int xLength) {
        Arrays.fill(dest, xStartOut, xStartOut + xLength, src[xStartIn]);
        float[][] lutX = cosineLut[xLogLength];
        for (int n = 1; n < xLength; n++) {
            final float[] lut = lutX[n - 1];
            final float s2 = src[xStartIn + n];
            for (int k = 0; k < xLength; k++)
                dest[xStartOut + k] += s2 * lut[k];
        }
    }

    public static void forwardDCTHorizontal(float[] src, float[] dest,
            int xStartIn, int xStartOut, int xLogLength, int xLength) {
        final float invLength = 1f / xLength;
        float d2 = src[xStartIn];
        for (int x = 1; x < xLength; ++x)
            d2 += src[xStartIn + x];
        dest[xStartOut] = d2 * invLength;
        for (int k = 1; k < xLength; ++k) {
            final float[] lut = cosineLut[xLogLength][k - 1];
            d2 = src[xStartIn] * lut[0];
            for (int n = 1; n < xLength; ++n)
                d2 += src[xStartIn + n] * lut[n];
            dest[xStartOut + k] = d2 * invLength;
        }
    }

    public static void inverseDCT2D(float[][] src, float[][] dest, Point startIn, Point startOut, Dimension size,
            float[][] scratchSpace0, float[][] scratchSpace1, boolean transposed) {
        int logHeight = ceilLog2(size.height);
        int logWidth = ceilLog2(size.width);
        if (transposed) {
            for (int y = 0; y < size.height; y++) {
                inverseDCTHorizontal(src[startIn.y + y], scratchSpace1[y],
                    startIn.x, 0, logWidth, size.width);
            }
            transposeMatrixInto(scratchSpace1, scratchSpace0, ZERO, ZERO, size.height, size.width);
            for (int y = 0; y < size.width; y++) {
                inverseDCTHorizontal(scratchSpace0[y], dest[startOut.y + y],
                    0, startOut.x, logHeight, size.height);
            }
        } else {
            transposeMatrixInto(src, scratchSpace0, startIn, ZERO, size.height, size.width);
            for (int y = 0; y < size.width; y++) {
                inverseDCTHorizontal(scratchSpace0[y], scratchSpace1[y],
                    0, 0, logHeight, size.height);
            }
            transposeMatrixInto(scratchSpace1, scratchSpace0, ZERO, ZERO, size.width, size.height);
            for (int y = 0; y < size.height; y++) {
                inverseDCTHorizontal(scratchSpace0[y], dest[startOut.y + y],
                    0, startOut.x, logWidth, size.width);
            }
        }
    }

    public static void forwardDCT2D(float[][] src, float[][] dest, Point startIn, Point startOut,
            Dimension length, float[][] scratchSpace0, float[][] scratchSpace1) {
        final int yLogLength = ceilLog2(length.height);
        final int xLogLength = ceilLog2(length.width);
        for (int y = 0; y < length.height; y++)
            forwardDCTHorizontal(src[y + startIn.y], scratchSpace0[y],
                startIn.x, 0, xLogLength, length.width);
        transposeMatrixInto(scratchSpace0, scratchSpace1, ZERO, ZERO, length.height, length.width);
        for (int x = 0; x < length.width; x++)
            forwardDCTHorizontal(scratchSpace1[x], scratchSpace0[x],
                0, 0, yLogLength, length.height);
        transposeMatrixInto(scratchSpace0, dest, ZERO, startOut, length.width, length.height);
    }

    public static void transposeMatrixInto(float[][] src, float[][] dest,
            Point srcStart, Point destStart, int srcHeight, int srcWidth) {
        for (int y = 0; y < srcHeight; y++) {
            final float[] srcy = src[srcStart.y + y];
            for (int x = 0; x < srcWidth; x++)
                dest[destStart.y + x][destStart.x + y] = srcy[srcStart.x + x];
        }
    }

    public static float[][] transposeMatrix(float[][] matrix, int height, int width) {
        final float[][] dest = new float[width][height];
        transposeMatrixInto(matrix, dest, ZERO, ZERO, height, width);
        return dest;
    }

    /**
     * @return ceil(log2(x + 1))
     */
    public static int ceilLog1p(long x) {
        return 64 - Long.numberOfLeadingZeros(x);
    }

    public static int ceilLog2(long x) {
        return ceilLog1p(x - 1);
    }

    public static int ceilDiv(int numerator, int denominator) {
        return ((numerator - 1) / denominator) + 1;
    }

    public static int floorLog1p(long x) {
        int c = ceilLog1p(x);
        // if x + 1 is not a power of 2
        if (((x + 1) & x) != 0)
            return c - 1;
        return c;
    }

    public static int min(int... a) {
        int result = a[0];
        for (int i = 1; i < a.length; i++)
            result = a[i] < result ? a[i] : result; // jit compiles to cmov on x86
        return result;
    }

    public static int max(int... a) {
        int result = a[0];
        for (int i = 1; i < a.length; i++)
            result = a[i] > result ? a[i] : result; // jit compiles to cmov on x86
        return result;
    }

    public static float max(float... a) {
        float result = a[0];
        for (int i = 1; i < a.length; i++)
            result = a[i] < result ? a[i] : result;
        return result;
    }

    public static float signedPow(float base, float exponent) {
        return (float)(base < 0 ? -Math.pow(-base, exponent) : Math.pow(base, exponent));
    }

    public static int clamp(int v, int a, int b, int c) {
        int lower = a < b ? a : b;
        int upper = lower ^ a ^ b;
        lower = lower < c ? lower : c;
        upper = upper > c ? upper : c;
        return v < lower ? lower : v > upper ? upper : v;
    }

    public static int clamp(int v, int a, int b) {
        int lower = a < b ? a : b;
        int upper = lower ^ a ^ b;
        return v < lower ? lower : v > upper ? upper : v;
    }

    public static float clamp(float v, float a, float b) {
        float lower = a < b ? a : b;
        float upper = a < b ? b : a;
        return v < lower ? lower : v > upper ? upper : v;
    }

    public static float[] matrixMutliply(final float[][] matrix, final float[] columnVector) {
        if (matrix == null)
            return columnVector;
        if (matrix[0].length > columnVector.length || columnVector.length == 0)
            throw new IllegalArgumentException();
        int extra = columnVector.length - matrix[0].length;
        float[] total = new float[matrix.length + extra];
        for (int y = 0; y < matrix.length; y++) {
            final float[] row = matrix[y];
            for (int x = 0; x < row.length; x++)
                total[y] += row[x] * columnVector[x];
        }
        if (extra != 0)
            System.arraycopy(columnVector, columnVector.length - extra, total, total.length - extra, extra);
        return total;
    }

    public static void matrixMutliply3InPlace(final float[][] matrix, final float[] columnVector) {
        final float a = matrix[0][0] * columnVector[0]
            + matrix[0][1] * columnVector[1] + matrix[0][2] * columnVector[2];
        final float b = matrix[1][0] * columnVector[0]
            + matrix[1][1] * columnVector[1] + matrix[1][2] * columnVector[2];
        final float c = matrix[2][0] * columnVector[0]
            + matrix[2][1] * columnVector[1] + matrix[2][2] * columnVector[2];
        columnVector[0] = a;
        columnVector[1] = b;
        columnVector[2] = c;
    }

    public static float[] matrixMutliply(final float[] rowVector, final float[][] matrix) {
        if (matrix == null)
            return rowVector;
        if (matrix.length != rowVector.length || rowVector.length == 0)
            throw new IllegalArgumentException();
        float[] total = new float[matrix[0].length];
        for (int y = 0; y < rowVector.length; y++) {
            final float[] row = matrix[y];
            for (int x = 0; x < total.length; x++)
                total[x] += rowVector[y] * row[x];
        }
        return total;
    }

    public static float[][] matrixMutliply(final float[][] left, final float[][] right) {
        if (left == null)
            return right;
        if (right == null)
            return left;
        if (left.length == 0 || left[0].length == 0 || right.length == 0 || left.length != right[0].length)
            throw new IllegalArgumentException();
        float[][] result = new float[left.length][right[0].length];
        for (int y = 0; y < right.length; y++)
            result[y] = matrixMutliply(left[y], right);
        return result;
    }

    public static float[][] matrixIdentity(int n) {
        float[][] identity = new float[n][n];
        for (int i = 0; i < n; i++)
            identity[i][i] = 1.0f;
        return identity;
    }

    public static float[][] matrixMutliply(float[][] a, float[][] b, float[][] c) {
        return matrixMutliply(matrixMutliply(a, b), c);
    }

    // expensive! try not to use on the fly
    public static float[][] invertMatrix3x3(float[][] matrix) {
        if (matrix == null)
            return null;
        if (matrix.length != 3)
            throw new IllegalArgumentException();
        float det = 0f;
        for (int c = 0; c < 3; c++) {
            if (matrix[c].length != 3)
                throw new IllegalArgumentException();
            int c1 = (c + 1) % 3;
            int c2 = (c + 2) % 3;
            det += matrix[c][0] * matrix[c1][1] * matrix[c2][2] - matrix[c][0] * matrix[c1][2] * matrix[c2][1];
        }
        if (det == 0f)
            return null;
        float invDet = 1f / det;
        float[][] inverse = new float[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                int x1 = (x + 1) % 3;
                int x2 = (x + 2) % 3;
                int y1 = (y + 1) % 3;
                int y2 = (y + 2) % 3;
                // because we're going cyclicly here we don't need to multiply by (-1)^(x + y)
                inverse[y][x] = (matrix[x1][y1] * matrix[x2][y2] - matrix[x2][y1] * matrix[x1][y2]) * invDet;
            }
        }
        return inverse;
    }

    public static int mirrorCoordinate(int coordinate, int size) {
        while (coordinate < 0 || coordinate >= size) {
            int tc = ~coordinate;
            coordinate = tc >= 0 ? tc : (size << 1) + tc;
        }
        return coordinate;
    }

    public static float floatFromF16(int bits16) {
        int mantissa = bits16 & 0x3FF;
        int biased_exp = (bits16 >>> 10) & 0x1F;
        int sign = (bits16 >>> 15) & 1;
        if (biased_exp == 31)
            return Float.NaN;
        if (biased_exp == 0)
            return (1 - 2 * sign) * mantissa / 16777216f;
        biased_exp += 127 - 15;
        mantissa <<= 13;
        sign <<= 31;
        int total = sign | (biased_exp << 23) | mantissa;
        return Float.intBitsToFloat(total);
    }

    public static float[][][] deepCopyOf(float[][][] array) {
        if (array == null)
            return null;
        float[][][] copy = new float[array.length][][];
        for (int i = 0; i < array.length; i++) {
            if (array[i] == null)
                continue;
            copy[i] = new float[array[i].length][];
            for (int j = 0; j < array[i].length; j++) {
                if (array[i][j] == null)
                    continue;
                copy[i][j] = Arrays.copyOf(array[i][j], array[i][j].length);
            }
        }
        return copy;
    }

    private MathHelper() {}
}
