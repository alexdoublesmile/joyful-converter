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
     * If false, re-encodes to H.264/AAC with high quality settings.
     * @param progressCallback  Callback to report progress (0.0-100.0)
     * @throws Exception If conversion fails
     */
    public void convertVideo(String inputPath, String outputPath, String outputFormat, boolean tryStreamCopy, Consumer<Double> progressCallback) throws Exception {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new IOException("Input file not found: " + inputPath);
        }

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            int sourceVideoCodec = grabber.getVideoCodec();
            int sourceAudioCodec = grabber.getAudioCodec();
            int sourcePixelFormat = grabber.getPixelFormat();

            System.out.println("Source Video Codec ID: " + sourceVideoCodec +
                    " (MPEG4 is " + avcodec.AV_CODEC_ID_MPEG4 + ", H264 is " + avcodec.AV_CODEC_ID_H264 + ")");
            System.out.println("Source Audio Codec ID: " + sourceAudioCodec);
            System.out.println("Source Pixel Format: " + sourcePixelFormat +
                    " (YUV420P is " + avutil.AV_PIX_FMT_YUV420P + ", BGR24 is " + avutil.AV_PIX_FMT_BGR24 + ")");


            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath,
                    grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels())) {

                recorder.setFormat(outputFormat);
                recorder.setFrameRate(grabber.getFrameRate()); // Always set frame rate
                // For audio, sample rate and channels are also fundamental
                recorder.setSampleRate(grabber.getSampleRate());
                recorder.setAudioChannels(grabber.getAudioChannels());


                if (tryStreamCopy) {
                    System.out.println("Attempting Stream Copy Mode (Preserve Original Quality)...");
                    // Copy original video stream
                    recorder.setVideoCodec(sourceVideoCodec);
                    // Copy original audio stream
                    recorder.setAudioCodec(sourceAudioCodec);

                    // When stream copying, do NOT set pixel format, CRF, preset, video quality, or audio quality,
                    // as these are encoding parameters. The original stream characteristics are copied.
                    // However, some metadata like bitrate might be useful if available and applicable.
                    if (grabber.getVideoBitrate() > 0) {
                        recorder.setVideoBitrate(grabber.getVideoBitrate());
                    }
                    if (grabber.getAudioBitrate() > 0) {
                        recorder.setAudioBitrate(grabber.getAudioBitrate());
                    }
                    System.out.println("Stream Copy: Video Codec = " + sourceVideoCodec + ", Audio Codec = " + sourceAudioCodec);

                } else {
                    System.out.println("Re-encoding Mode (High Quality H.264/AAC)...");
                    // --- Video Settings: Re-encode to H.264 ---
                    recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                    recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P); // Standard for H.264 compatibility
                    recorder.setVideoOption("crf", "18"); // Visually lossless
                    recorder.setVideoOption("preset", "slow"); // Good balance of quality and compression speed
                    System.out.println("Re-encode Video: H.264, CRF=18, Preset=slow, PixelFormat=YUV420P");

                    // --- Audio Settings: Re-encode to AAC ---
                    recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                    recorder.setAudioQuality(1); // Good VBR quality (e.g., ~192-256kbps stereo for music)
                    // For speech, lower might be fine. 0 is often highest VBR.
                    System.out.println("Re-encode Audio: AAC, Quality=1 (Good VBR)");
                }

                recorder.start();

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

                // In stream copy mode, we grab and record. FFmpeg handles the packet copying.
                // In encoding mode, grabbing provides decoded frames, recording encodes them.
                while ((frame = grabber.grab()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Conversion was cancelled.");
                    }
                    recorder.record(frame); // Records video, audio, and other frame types
                    processedFrames++;

                    if (progressCallback != null && effectiveTotalFrames > 0) {
                        // Update progress less frequently to avoid flooding UI thread
                        if (processedFrames % 20 == 0 || processedFrames == effectiveTotalFrames) {
                            double progress = (processedFrames * 100.0) / effectiveTotalFrames;
                            progressCallback.accept(Math.min(100.0, progress));
                        }
                    } else if (progressCallback != null && processedFrames % 100 == 0) {
                        // For indeterminate progress, maybe a periodic ping or nothing
                        // progressCallback.accept(-1.0); // Or some other indicator
                    }
                }

                if (progressCallback != null) {
                    progressCallback.accept(100.0); // Ensure 100% is sent at the end
                }

            } finally {
                grabber.stop();
            }
        }
    }
}
