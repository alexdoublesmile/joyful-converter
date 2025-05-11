# AVI to MP4 Video Converter

A professional desktop application for converting AVI video files to MP4 format without quality loss.

## Features

- Simple and intuitive user interface
- Lossless video conversion from AVI to MP4
- Progress tracking during conversion
- File selection dialogs for input and output files
- Cross-platform compatibility (Windows, macOS, Linux)

## Requirements

- Java 17 or later
- Maven 3.6 or later

## Building the Application

1. Clone or download this repository
2. Open a terminal/command prompt in the project directory
3. Run the following command to build the application:

```
mvn clean package
```

This will create an executable JAR file in the `target` directory.

## Running the Application

After building, you can run the application using either of these methods:

### Method 1: Using Java

```
java -jar target/joyful-converter-1.0-SNAPSHOT.jar
```

### Method 2: Using Maven

```
mvn javafx:run
```

## Usage Instructions

1. Click "Browse" next to the input field to select an AVI video file
2. The output field will automatically be populated with the same filename but with .mp4 extension
3. You can click "Browse" next to the output field to change the output location
4. Check "Preserve original quality" if you want lossless conversion
5. Click "Convert" to start the conversion process
6. The progress bar will show the conversion progress
7. A dialog will appear when the conversion is complete

## Troubleshooting

- If you encounter "FFmpeg not found" errors, ensure that FFmpeg is installed on your system, or use the bundled version included with JavaCV
- For memory issues with large video files, increase the JVM heap size using `-Xmx` parameter (e.g., `java -Xmx2g -jar avi-to-mp4-converter-1.0-SNAPSHOT.jar`)

## License

This project is licensed under the MIT License - see LICENSE file for details.