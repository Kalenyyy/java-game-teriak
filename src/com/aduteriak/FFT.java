package com.aduteriak;

/**
 * Implementasi FFT (Fast Fourier Transform) Cooley-Tukey radix-2
 *
 * FFT memungkinkan kita mendekomposisi sinyal audio menjadi komponen frekuensi.
 * Dengan ini kita bisa mengukur energi di range frekuensi tertentu (teriakan).
 */
public class FFT {
    private int size;
    private double[] cosTable;
    private double[] sinTable;

    public FFT(int size) {
        if ((size & (size - 1)) != 0) {
            throw new IllegalArgumentException("Size harus power of 2");
        }
        this.size = size;

        // Pre-compute twiddle factors (cosine dan sine)
        // untuk menghindari perhitungan trigonometri yang mahal
        cosTable = new double[size / 2];
        sinTable = new double[size / 2];
        for (int i = 0; i < size / 2; i++) {
            double angle = 2 * Math.PI * i / size;
            cosTable[i] = Math.cos(angle);
            sinTable[i] = Math.sin(angle);
        }
    }

    /**
     * In-place FFT menggunakan algoritma Cooley-Tukey
     *
     * Input: real[] = [r0, r1, r2, ..., r(n-1)]
     * Output: fftBuffer[] = [R0, I0, R1, I1, R2, I2, ...]
     *         dimana R = real part, I = imaginary part
     */
    public void transform(double[] realBuffer) {
        int N = size;

        // Stage 1: Bit-reversal permutation
        bitReversalPermutation(realBuffer);

        // Stage 2: Iterative FFT computation
        for (int s = 1; s <= log2(N); s++) {
            int m = 1 << s;  // m = 2^s
            int halfM = m >> 1;

            for (int k = 0; k < N; k += m) {
                for (int j = 0; j < halfM; j++) {
                    int t = k + j;
                    int u = t + halfM;

                    // Twiddle factor
                    int twiddleIdx = j * (N / m);
                    double wr = cosTable[twiddleIdx];
                    double wi = -sinTable[twiddleIdx];

                    // Butterfly operation
                    // real parts: realBuffer[t*2] dan realBuffer[u*2]
                    // imag parts: realBuffer[t*2+1] dan realBuffer[u*2+1]

                    double tr = realBuffer[u * 2];
                    double ti = realBuffer[u * 2 + 1];

                    // Multiply (tr + j*ti) * (wr + j*wi)
                    double tempReal = tr * wr - ti * wi;
                    double tempImag = tr * wi + ti * wr;

                    // Subtract
                    realBuffer[u * 2] = realBuffer[t * 2] - tempReal;
                    realBuffer[u * 2 + 1] = realBuffer[t * 2 + 1] - tempImag;

                    // Add
                    realBuffer[t * 2] = realBuffer[t * 2] + tempReal;
                    realBuffer[t * 2 + 1] = realBuffer[t * 2 + 1] + tempImag;
                }
            }
        }
    }

    /**
     * Bit-reversal permutation untuk FFT
     */
    private void bitReversalPermutation(double[] buffer) {
        int N = size;
        for (int i = 0; i < N; i++) {
            int j = reverseBits(i, N);
            if (i < j) {
                // Swap real parts
                double temp = buffer[i * 2];
                buffer[i * 2] = buffer[j * 2];
                buffer[j * 2] = temp;

                // Swap imaginary parts
                temp = buffer[i * 2 + 1];
                buffer[i * 2 + 1] = buffer[j * 2 + 1];
                buffer[j * 2 + 1] = temp;
            }
        }
    }

    /**
     * Reverse bits dari integer dalam n-bit representation
     */
    private int reverseBits(int x, int N) {
        int result = 0;
        for (int i = 0; i < log2(N); i++) {
            result = (result << 1) | (x & 1);
            x >>= 1;
        }
        return result;
    }

    /**
     * Logarithm base 2
     */
    private int log2(int N) {
        return 31 - Integer.numberOfLeadingZeros(N);
    }
}