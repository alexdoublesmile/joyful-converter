# Joyful Converter: AVI Video Conversion Tool

A desktop application built with JavaFX for converting AVI video files to MP4 or MKV formats, prioritizing lossless conversion where possible.

## Features

* Simple and intuitive user interface.
* Select individual AVI files or entire folders (including subdirectories) for batch conversion.
* Choose MP4 or MKV as the output container format.
* **Intelligent Conversion Strategy:**
    1.  **Lossless Remux (Attempt 1):** Tries to copy the original video and audio streams directly into the chosen container (MP4 or MKV) without re-encoding, preserving original quality (if codecs are compatible). This is the default behavior.
    2.  **Lossless Remux Fallback (MKV):** If MP4 remuxing fails due to compatibility issues, it automatically attempts to remux into an MKV container (often more flexible).
    3.  **Re-encode Fallback (Lossy):** If all remuxing attempts fail, the video is re-encoded to H.264 (video) and AAC (audio) into the originally selected container format (MP4 or MKV). This ensures maximum compatibility but may result in some quality loss compared to the original AVI.
* Option to disable the initial lossless remux attempts and force re-encoding directly.
* Option to automatically delete original AVI files after successful conversion.
* Detailed progress tracking (overall, current directory, current file with percentage).
* Post-conversion statistics summarizing how many files were remuxed (lossless) vs. re-encoded (lossy), including a list of re-encoded files.
* Cross-platform compatibility (Windows, macOS, Linux).

## Requirements

* Java 17 or later
* Maven 3.6 or later (for building)
* FFmpeg libraries (usually bundled via JavaCV dependency)

## Building the Application

1.  Clone or download this repository.
2.  Open a terminal/command prompt in the project directory.
3.  Run the following command to build the application:
    ```bash
    mvn clean package
    ```
    This will create an executable JAR file (e.g., `joyful-converter-1.0.jar`) in the `target` directory.

## Running the Application

After building, you can run the application using one of these methods:

### Method 1: Using Java (Recommended)

```bash
 java -jar target/joyful-converter-1.0.jar
```

### Method 2: Using Maven

```
mvn javafx:run
```

## Usage Instructions

1. Use the File or Folder button to select your input AVI source.
2. Use the Browse button to select the output directory where converted files will be saved. A suggestion will often be pre-filled.
3. Select the desired Output Format (MP4 or MKV).
4. Preserve original quality checkbox:
5. Checked (Default): Attempts lossless remuxing first, with fallbacks as described above.
6. Unchecked: Skips remuxing attempts and directly re-encodes to H.264/AAC (lossy).
7. Replace original file(s) checkbox: If checked, the original AVI file will be deleted after its conversion is successful. Use with caution!
8. Click Convert to start the process.
9. Monitor the progress bars and status labels.
10. A summary dialog with statistics will appear upon completion or failure.

## Troubleshooting
- FFmpeg Errors: If you encounter errors related to FFmpeg not being found or load errors, ensure your environment is set up correctly or check JavaCV documentation for platform-specific requirements. The javacv-platform dependency in pom.xml should handle bundling native libraries.
- Memory Issues: For very large files or numerous batch conversions, you might need to increase the JVM heap size when running the JAR:

```bash
 java -Xmx2g -jar target/joyful-converter-1.0.jar
```
(Adjust 2g - 2 gigabytes - as needed).
- Conversion Failures: Some AVI files use obscure or incompatible codecs that even FFmpeg/JavaCV cannot handle correctly for remuxing or even re-encoding. Check the console output or error dialogs for details.
## License

This project is licensed under the MIT License - see LICENSE file for details.