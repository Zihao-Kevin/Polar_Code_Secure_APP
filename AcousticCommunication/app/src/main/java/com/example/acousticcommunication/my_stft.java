package com.example.acousticcommunication;

import com.example.acousticcommunication.fftpack.RealDoubleFFT;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.io.FileInputStream;
import java.io.IOException;

import static java.lang.Math.PI;
import static java.lang.Math.cos;

public class my_stft {
    public double fs = 22050;
    public int wlen = 1024;
    public int hop = wlen / 8;
    public int nfft = 4 * wlen;
    int NUP = (int) Math.ceil(((double) (1 + nfft)) / 2);

    double[] f = new double[(int)NUP + 10];

    public double[] mySTFT() throws IOException
    {

        double[] signal = ReadWaveFile("/storage/emulated/0/OFDMRecorder/track.wav");
        double[] x = new double[signal.length / 2];
        double[] t = new double[signal.length];
        for (int i = 0; i < signal.length; i ++ )
        {
            if (i % 2 == 0)
                x[i / 2] = signal[i];
            t[i] = i / fs;
        }
        double[] wnd = new double[wlen];
        for (int i=0; i<wnd.length; i++) {
            wnd[i] = 0.35875 - 0.48829*cos(2*PI*i/(wnd.length-1))+ 0.14128*cos(4*PI*i/(wnd.length-1)) - 0.01168*cos(6*PI*i/(wnd.length-1));
        }

        double L = 1 + Math.floor((x.length - wlen) / hop);
        RealMatrix STFT = new Array2DRowRealMatrix((int)NUP, (int)(L + 10));


        double[] xw = new double[nfft];

        for (int l = 0; l <= L - 1; l ++ )
        {
            for (int i = 0; i < wlen; i ++ ) xw[i] = x[i + l * hop] * wnd[i];
            int tmp = wlen;
            while (tmp < nfft)
            {
                xw[tmp] = 0;
                tmp ++ ;
            }
            RealDoubleFFT realdoublefft = new RealDoubleFFT(nfft);
            realdoublefft.ft(xw);
            double[] tt = new double[NUP];
            for (int i = 0; i < NUP; i ++ ) tt[i] = xw[i];
            STFT.setColumn(l, tt);
        }

        double[] T = new double[(int) L];
        for (int i = 0; i < L; i ++ ) T[i] = (wlen / 2 + i * hop) / fs;
        for (int i = 0; i <= NUP - 1; i ++ ) f[i] = i * fs / nfft;

        return f;
    }

    static double[] ReadWaveFile(String name) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(name);
        byte[] chunk = new byte[4];
        for (int i = 0; i < 11; i++)
            fileInputStream.read(chunk);
        long size = 0;
        for (int i = 3; i >= 0; i--)
            size = (size << 8) | (chunk[i] & 0xff);
        byte[] content = new byte[(int) size];
        fileInputStream.read(content);
        return ByteArrayToDoubleArray(content);
    }
    private static double[] ByteArrayToDoubleArray(byte[] b) {
        double[] result = new double[b.length / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = ((short) b[2 * i + 1] << 8) | ((short) b[2 * i] & 0xff);
            result[i] /= Short.MAX_VALUE;
        }
        return result;
    }
}
