package org.joymutlu.joyfulconverter.service;

public enum ConversionResultStatus {
    REMUX_MP4_OK,
    REMUX_MKV_OK, // Used for direct MKV or MP4->MKV fallback
    REENCODE_OK,
    FAILED;

    public static ConversionResultStatus resolveRemuxResult(String format) {
        if ("mp4".equalsIgnoreCase(format)) {
            return ConversionResultStatus.REMUX_MP4_OK;
        }
        if ("mkv".equalsIgnoreCase(format)) {
            return ConversionResultStatus.REMUX_MKV_OK;
        }
        return FAILED;
    }

    public static ConversionResultStatus resolveResult(String outputFormat) {
        return REENCODE_OK;
    }
}
