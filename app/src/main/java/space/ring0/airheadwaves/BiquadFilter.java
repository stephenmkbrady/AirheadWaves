package space.ring0.airheadwaves;

/**
 * Biquad Filter for audio DSP effects
 *
 * Supports:
 * - Low shelf filter (bass boost/cut)
 * - High shelf filter (treble boost/cut)
 *
 * Used by both AudioCaptureService (transmit) and AudioPlaybackService (receive)
 */
public class BiquadFilter {
    private float a1, a2, b0, b1, b2;
    private float x1, x2, y1, y2;
    private final int sampleRate;

    public BiquadFilter(int sampleRate) {
        this.sampleRate = sampleRate;
        b0 = 1.0f;
        b1 = 0.0f;
        b2 = 0.0f;
        a1 = 0.0f;
        a2 = 0.0f;
        x1 = 0.0f;
        x2 = 0.0f;
        y1 = 0.0f;
        y2 = 0.0f;
    }

    public void setLowShelf(float gainDb, float centerFreq) {
        float q = 0.707f;
        float A = (float) Math.pow(10, gainDb / 40.0);
        float w0 = (float) (2.0 * Math.PI * centerFreq / this.sampleRate);
        float cos_w0 = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / (2.0f * q));

        float a0_ = (A + 1) + (A - 1) * cos_w0 + 2 * (float)Math.sqrt(A) * alpha;
        this.a1 = -2 * ((A - 1) + (A + 1) * cos_w0);
        this.a2 = (A + 1) + (A - 1) * cos_w0 - 2 * (float)Math.sqrt(A) * alpha;
        this.b0 = A * ((A + 1) - (A - 1) * cos_w0 + 2 * (float)Math.sqrt(A) * alpha);
        this.b1 = 2 * A * ((A - 1) - (A + 1) * cos_w0);
        this.b2 = A * ((A + 1) - (A - 1) * cos_w0 - 2 * (float)Math.sqrt(A) * alpha);

        this.a1 /= a0_;
        this.a2 /= a0_;
        this.b0 /= a0_;
        this.b1 /= a0_;
        this.b2 /= a0_;
    }

    public void setHighShelf(float gainDb, float centerFreq) {
        float q = 0.707f;
        float A = (float) Math.pow(10, gainDb / 40.0);
        float w0 = (float) (2.0 * Math.PI * centerFreq / this.sampleRate);
        float cos_w0 = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / (2.0f * q));

        float a0_ = (A + 1) - (A - 1) * cos_w0 + 2 * (float)Math.sqrt(A) * alpha;
        this.a1 = 2 * ((A - 1) - (A + 1) * cos_w0);
        this.a2 = (A + 1) - (A - 1) * cos_w0 - 2 * (float)Math.sqrt(A) * alpha;
        this.b0 = A * ((A + 1) + (A - 1) * cos_w0 + 2 * (float)Math.sqrt(A) * alpha);
        this.b1 = -2 * A * ((A - 1) + (A + 1) * cos_w0);
        this.b2 = A * ((A + 1) + (A - 1) * cos_w0 - 2 * (float)Math.sqrt(A) * alpha);

        this.a1 /= a0_;
        this.a2 /= a0_;
        this.b0 /= a0_;
        this.b1 /= a0_;
        this.b2 /= a0_;
    }

    public float process(float in) {
        float out = b0 * in + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1;
        x1 = in;
        y2 = y1;
        y1 = out;
        return out;
    }
}
