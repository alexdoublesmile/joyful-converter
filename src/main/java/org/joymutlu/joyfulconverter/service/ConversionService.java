package org.joymutlu.joyfulconverter.service;

import java.io.File;
import java.util.function.Consumer;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

public class ConversionService {

    /**
     * Converts an AVI video file to MP4 format without quality loss
     *
     * @param inputPath Path to the input AVI file
     * @param outputPath Path to save the output MP4 file
     * @param preserveQuality Whether to preserve original quality (lossless)
     * @param progressCallback Callback to report progress (0-100)
     * @throws Exception If conversion fails
     */
    public void convertAviToMp4(String inputPath, String outputPath, boolean preserveQuality, Consumer<Double> progressCallback) throws Exception {
        File inputFile = new File(inputPath);
        long fileSize = inputFile.length();

        // Open input file with FFmpegFrameGrabber
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            // Configure recorder for the output file
            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath,
                    grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels())) {

                // Copy essential stream properties
                recorder.setFormat("mp4");
                recorder.setFrameRate(grabber.getFrameRate());
                recorder.setSampleRate(grabber.getSampleRate());
                recorder.setAudioChannels(grabber.getAudioChannels());

                // High quality/lossless encoding settings
                if (preserveQuality) {
                    // Video settings for high quality
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                    recorder.setVideoQuality(0); // Lower is better quality, 0 is lossless
                    recorder.setPixelFormat(grabber.getPixelFormat());

                    // Audio settings for high quality
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                    recorder.setAudioQuality(0); // Lower is better quality

                    // Additional high quality options
                    recorder.setOption("crf", "18"); // Lower CRF = higher quality (18 is visually lossless)
                    recorder.setOption("preset", "slow"); // Slower preset = better compression
                } else {
                    // Standard quality settings
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                    recorder.setOption("crf", "23"); // Default quality
                    recorder.setOption("preset", "medium"); // Balanced preset
                }

                recorder.start();

                // Process frames
                Frame frame;
                long totalFrames = grabber.getLengthInFrames();
                long processedFrames = 0;

                // Handle case where frame count is unknown
                if (totalFrames <= 0) {
                    // Estimate based on duration and framerate
                    double duration = grabber.getLengthInTime() / 1000000.0; // in seconds
                    double fps = grabber.getFrameRate();
                    totalFrames = Math.round(duration * fps);

                    // If still unknown, use 1000 as placeholder for progress calculation
                    if (totalFrames <= 0) {
                        totalFrames = 1000;
                    }
                }

                while ((frame = grabber.grab()) != null) {
                    recorder.record(frame);
                    processedFrames++;

                    // Update progress every few frames
                    if (processedFrames % 10 == 0 && progressCallback != null) {
                        double progress = (processedFrames * 100.0) / totalFrames;
                        progressCallback.accept(Math.min(99.0, progress)); // Cap at 99% until finished
                    }
                }

                // Indicate 100% completion
                if (progressCallback != null) {
                    progressCallback.accept(100.0);
                }
            }
        }
    }
}
