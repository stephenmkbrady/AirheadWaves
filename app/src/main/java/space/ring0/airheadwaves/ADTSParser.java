package space.ring0.airheadwaves;

import android.util.Log;

/**
 * ADTS (Audio Data Transport Stream) Frame Parser
 *
 * Parses AAC audio frames with ADTS headers to extract:
 * - Sample rate
 * - Channel configuration
 * - Frame length
 *
 * ADTS Header Format (7 bytes):
 * Byte 0: 0xFF (sync word)
 * Byte 1: 0xF1 (sync word + MPEG-4 + Layer + No CRC)
 * Byte 2: Profile (2 bits) + Frequency Index (4 bits) + Channel Config (1 bit)
 * Byte 3: Channel Config (2 bits) + Frame Length (2 bits)
 * Byte 4: Frame Length (8 bits)
 * Byte 5: Frame Length (3 bits) + Buffer Fullness (5 bits)
 * Byte 6: Buffer Fullness (6 bits) + Number of AAC frames (2 bits)
 */
public class ADTSParser {
    private static final String TAG = "ADTSParser";
    private static final int ADTS_HEADER_SIZE = 7;

    // Sample rate table (MPEG-4)
    private static final int[] SAMPLE_RATES = {
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
        16000, 12000, 11025, 8000, 7350, 0, 0, 0
    };

    public static class ADTSFrame {
        public int sampleRate;
        public int channels;
        public int frameLength;  // Total length including header
        public int profile;
        public boolean isValid;

        public int getPayloadLength() {
            return frameLength - ADTS_HEADER_SIZE;
        }
    }

    /**
     * Parse ADTS header from byte array
     * @param data Byte array containing ADTS frame
     * @param offset Offset to start of ADTS header
     * @return Parsed ADTS frame info, or null if invalid
     */
    public static ADTSFrame parseHeader(byte[] data, int offset) {
        if (data == null || data.length < offset + ADTS_HEADER_SIZE) {
            return null;
        }

        ADTSFrame frame = new ADTSFrame();

        // Check sync word (0xFFF)
        int syncWord = ((data[offset] & 0xFF) << 4) | ((data[offset + 1] & 0xF0) >> 4);
        if (syncWord != 0xFFF) {
            Log.w(TAG, "Invalid ADTS sync word: " + Integer.toHexString(syncWord));
            frame.isValid = false;
            return frame;
        }

        // Extract profile (AAC LC = 2, which is represented as 1 in ADTS)
        int profile = ((data[offset + 2] & 0xC0) >> 6) + 1;
        frame.profile = profile;

        // Extract frequency index
        int freqIndex = (data[offset + 2] & 0x3C) >> 2;
        if (freqIndex >= SAMPLE_RATES.length || SAMPLE_RATES[freqIndex] == 0) {
            Log.w(TAG, "Invalid frequency index: " + freqIndex);
            frame.isValid = false;
            return frame;
        }
        frame.sampleRate = SAMPLE_RATES[freqIndex];

        // Extract channel configuration
        int chanCfg = ((data[offset + 2] & 0x01) << 2) | ((data[offset + 3] & 0xC0) >> 6);
        frame.channels = chanCfg;

        // Extract frame length (13 bits)
        int frameLength = ((data[offset + 3] & 0x03) << 11) |
                         ((data[offset + 4] & 0xFF) << 3) |
                         ((data[offset + 5] & 0xE0) >> 5);
        frame.frameLength = frameLength;

        frame.isValid = true;

        Log.d(TAG, String.format("ADTS Frame: %dHz, %dch, %d bytes, profile=%d",
            frame.sampleRate, frame.channels, frame.frameLength, frame.profile));

        return frame;
    }

    /**
     * Find next ADTS sync word in buffer
     * @param data Byte array to search
     * @param offset Starting offset
     * @return Offset of sync word, or -1 if not found
     */
    public static int findSyncWord(byte[] data, int offset) {
        if (data == null || data.length < offset + 2) {
            return -1;
        }

        for (int i = offset; i < data.length - 1; i++) {
            // Check for 0xFFF sync pattern
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xF0) == 0xF0) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Validate that we have a complete ADTS frame
     * @param data Buffer containing data
     * @param offset Offset to ADTS header
     * @return True if complete frame is available
     */
    public static boolean hasCompleteFrame(byte[] data, int offset) {
        if (data == null || data.length < offset + ADTS_HEADER_SIZE) {
            return false;
        }

        ADTSFrame frame = parseHeader(data, offset);
        if (frame == null || !frame.isValid) {
            return false;
        }

        // Check if we have the complete frame
        return data.length >= offset + frame.frameLength;
    }

    /**
     * Get frequency index for given sample rate (for ADTS header creation)
     */
    public static int getFrequencyIndex(int sampleRate) {
        for (int i = 0; i < SAMPLE_RATES.length; i++) {
            if (SAMPLE_RATES[i] == sampleRate) {
                return i;
            }
        }
        return 4; // Default to 44100 Hz
    }
}
