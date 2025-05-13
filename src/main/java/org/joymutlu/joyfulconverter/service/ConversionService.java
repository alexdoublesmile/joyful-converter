package org.joymutlu.joyfulconverter.service;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

public class ConversionService {

    /**
     * Converts a video file to the specified output format.
     *
     * @param inputPath         Path to the input video file (e.g., .avi)
     * @param outputPath        Path to save the output video file (e.g., .mp4, .mkv)
     * @param outputFormat      The desired output format ("mp4", "mkv")
     * @param tryStreamCopy     If true, attempts to copy the original video and audio streams without re-encoding.
     *                          If false, re-encodes to H.264/AAC with high quality settings.
     * @param progressCallback  Callback to report progress (0.0-100.0)
     * @throws Exception If conversion fails
     */
    public ConversionResultStatus convertVideo(String inputPath, String outputPath, String outputFormat, boolean tryStreamCopy, Consumer<Double> progressCallback) throws Exception {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("Input file not found: " + inputPath);
        }

        if (tryStreamCopy) {
            try {
                // First attempt: Try stream copy with user-selected format
                return streamCopyVideo(inputPath, outputPath, outputFormat, progressCallback);
            } catch (Exception e) {
                String errorMessage = e.getMessage();

                // Check if the error is the timebase/codec issue
                if (errorMessage != null && (errorMessage.contains("error -22") ||
                        errorMessage.contains("Could not open video codec") ||
                        errorMessage.contains("timebase") && errorMessage.contains("not supported"))) {

                    System.err.println("Stream copy failed with compatibility error: " + errorMessage);

                    // If we already tried MKV or failed with something else, skip to re-encode
                    if ("mkv".equalsIgnoreCase(outputFormat)) {
                        System.err.println("MKV stream copy failed. Falling back to re-encode.");
                    } else {
                        // Try MKV as fallback for remuxing
                        try {
                            System.out.println("Trying MKV as fallback container for stream copy...");
                            String mkvOutputPath = outputPath.substring(0, outputPath.lastIndexOf('.')) + ".mkv";
                            return streamCopyVideo(inputPath, mkvOutputPath, "mkv", progressCallback);
                        } catch (Exception mkvError) {
                            System.err.println("MKV fallback also failed: " + mkvError.getMessage());
                            // Both attempts failed, continue to re-encode
                        }
                    }
                } else {
                    // Not a timebase/codec error, but still fail, log it
                    System.err.println("Stream copy failed with error: " + errorMessage);
                }

                // Fall back to re-encode with original format
                System.out.println("Falling back to full re-encode with " + outputFormat);
            }
        }

        // If we get here, either we're not trying stream copy or all stream copy attempts failed
        // Proceed with full re-encode
        return reEncodeVideo(inputPath, outputPath, outputFormat, progressCallback);
    }

    /**
     * Attempts to remux (stream copy) the video without re-encoding.
     *
     * @param inputPath Path to input file
     * @param outputPath Path to output file
     * @param outputFormat Output format (mp4, mkv)
     * @param progressCallback Progress reporting callback
     * @throws Exception If remuxing fails
     */
    private ConversionResultStatus streamCopyVideo(String inputPath, String outputPath, String outputFormat, Consumer<Double> progressCallback) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            int sourceVideoCodec = grabber.getVideoCodec();
            int sourceAudioCodec = grabber.getAudioCodec();

            System.out.println("Stream Copy Mode: Attempting to remux to " + outputFormat);
            System.out.println("Source Video Codec ID: " + sourceVideoCodec +
                    " (MPEG4 is " + avcodec.AV_CODEC_ID_MPEG4 + ", H264 is " + avcodec.AV_CODEC_ID_H264 + ")");
            System.out.println("Source Audio Codec ID: " + sourceAudioCodec);

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath,
                    grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels())) {

                recorder.setFormat(outputFormat);
                recorder.setFrameRate(grabber.getFrameRate());
                recorder.setSampleRate(grabber.getSampleRate());
                recorder.setAudioChannels(grabber.getAudioChannels());

                // Set stream copy mode
                recorder.setVideoCodec(sourceVideoCodec);
                recorder.setAudioCodec(sourceAudioCodec);

                // Copy bitrates if available
                if (grabber.getVideoBitrate() > 0) {
                    recorder.setVideoBitrate(grabber.getVideoBitrate());
                }
                if (grabber.getAudioBitrate() > 0) {
                    recorder.setAudioBitrate(grabber.getAudioBitrate());
                }

                recorder.start();

                processFrames(grabber, recorder, progressCallback);
            }
        }
        return ConversionResultStatus.resolveRemuxResult(outputFormat);
    }

    /**
     * Re-encodes the video with H.264/AAC high quality settings.
     *
     * @param inputPath Path to input file
     * @param outputPath Path to output file
     * @param outputFormat Output format (mp4, mkv)
     * @param progressCallback Progress reporting callback
     * @throws Exception If re-encoding fails
     */
    private ConversionResultStatus reEncodeVideo(String inputPath, String outputPath, String outputFormat, Consumer<Double> progressCallback) throws Exception {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            System.out.println("Re-encoding Mode: Converting to H.264/AAC with format " + outputFormat);

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath,
                    grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels())) {

                recorder.setFormat(outputFormat);
                recorder.setFrameRate(grabber.getFrameRate());
                recorder.setSampleRate(grabber.getSampleRate());
                recorder.setAudioChannels(grabber.getAudioChannels());

                // Video settings for H.264
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                recorder.setVideoOption("crf", "18"); // Visually lossless
                recorder.setVideoOption("preset", "slow"); // Good balance of quality and compression speed

                // Audio settings for AAC
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                recorder.setAudioQuality(1); // Good VBR quality

                recorder.start();

                processFrames(grabber, recorder, progressCallback);
            }
            return ConversionResultStatus.REENCODE_OK;

        } catch (Exception ex) {
            System.err.println("All fallbacks are failed: " + ex.getMessage());
            return ConversionResultStatus.FAILED;
        }
    }

    /**
     * Process frames from grabber to recorder with progress reporting.
     */
    private void processFrames(FFmpegFrameGrabber grabber, FFmpegFrameRecorder recorder, Consumer<Double> progressCallback) throws Exception {
        Frame frame;
        long totalFrames = grabber.getLengthInFrames();
        long processedFrames = 0;

        if (totalFrames <= 0) { // Estimate if not available
            double duration = grabber.getLengthInTime() / 1000000.0; // seconds
            double fps = grabber.getFrameRate();
            if (duration > 0 && fps > 0) {
                totalFrames = Math.round(duration * fps);
            } else {
                totalFrames = -1; // Indicate unknown for progress
            }
        }

        final long effectiveTotalFrames = totalFrames;

        while ((frame = grabber.grab()) != null) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Conversion was cancelled.");
            }
            recorder.record(frame);
            processedFrames++;

            if (progressCallback != null && effectiveTotalFrames > 0) {
                // Update progress less frequently to avoid flooding UI thread
                if (processedFrames % 20 == 0 || processedFrames == effectiveTotalFrames) {
                    double progress = (processedFrames * 100.0) / effectiveTotalFrames;
                    progressCallback.accept(Math.min(100.0, progress));
                }
            } else if (progressCallback != null && processedFrames % 100 == 0) {
                // For indeterminate progress, maybe a periodic ping
                // progressCallback.accept(-1.0);
            }
        }

        if (progressCallback != null) {
            progressCallback.accept(100.0); // Ensure 100% is sent at the end
        }
    }
}