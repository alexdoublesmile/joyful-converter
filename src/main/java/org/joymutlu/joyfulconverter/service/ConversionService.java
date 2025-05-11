package org.joymutlu.joyfulconverter.service;

import java.io.File;
import java.util.function.Consumer;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

public class ConversionService {

    /**
     * Converts an AVI video file to MP4 format
     *
     * @param inputPath       Path to the input AVI file
     * @param outputPath      Path to save the output MP4 file
     * @param preserveQuality Whether to preserve original quality (lossless)
     * @param progressCallback Callback to report progress (0-100)
     * @throws Exception If conversion fails
     */
    public void convertAviToMp4(String inputPath, String outputPath, boolean preserveQuality, Consumer<Double> progressCallback) throws Exception {
        File inputFile = new File(inputPath);
        // long fileSize = inputFile.length(); // fileSize is not used

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

                // Set pixel format consistently for H.264
                // yuv420p is widely compatible for H.264 and matches your input.
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);

                if (preserveQuality) {
                    // Video settings for high quality
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                    recorder.setVideoQuality(0); // 0 can be lossless for some codecs, but for H.264 'crf' is better.
                    // Using CRF 18 is visually lossless.

                    // Audio settings for high quality
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                    recorder.setAudioQuality(0); // For AAC, this might not be the typical way; VBR or bitrate is common.
                    // However, JavaCV might map this to a high-quality setting.

                    // Additional high quality options
                    recorder.setOption("crf", "18"); // Lower CRF = higher quality (18 is visually lossless)
                    recorder.setOption("preset", "slow"); // Slower preset = better compression
                } else {
                    // Standard quality settings
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                    // recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P); // Already set above, applies to both cases
                    recorder.setOption("crf", "23"); // Default CRF, good quality/size balance
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
                    if (duration > 0 && fps > 0) {
                        totalFrames = Math.round(duration * fps);
                    } else {
                        // If still unknown, use a large enough number for progress, or handle differently
                        // For now, using a placeholder might make progress inaccurate if duration/fps is truly zero/unknown
                        totalFrames = 1000; // Fallback placeholder, progress might not be accurate
                    }
                }

                // Check if totalFrames is still not positive to prevent division by zero or negative
                if (totalFrames <= 0) {
                    // If totalFrames couldn't be determined, progress calculation will be problematic.
                    // Consider disabling precise progress or using a time-based estimation.
                    // For this fix, we'll proceed, but be aware of potential progress issues.
                    System.err.println("Warning: Could not determine total frames. Progress may be inaccurate.");
                    totalFrames = Long.MAX_VALUE; // Avoid division by zero, progress will stay low
                }


                while ((frame = grabber.grab()) != null) {
                    recorder.record(frame);
                    processedFrames++;

                    // Update progress every few frames
                    if (progressCallback != null && totalFrames > 0 && totalFrames != Long.MAX_VALUE) { // ensure totalFrames is valid for calculation
                        if (processedFrames % 10 == 0) {
                            double progress = (processedFrames * 100.0) / totalFrames;
                            progressCallback.accept(Math.min(99.0, progress)); // Cap at 99% until finished
                        }
                    } else if (progressCallback != null && totalFrames == Long.MAX_VALUE) {
                        // Indeterminate progress, maybe update status without percentage
                        // For now, no progress update in this specific edge case
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
