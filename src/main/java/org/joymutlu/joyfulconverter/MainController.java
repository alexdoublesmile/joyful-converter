package org.joymutlu.joyfulconverter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.joymutlu.joyfulconverter.service.ConversionResultStatus;
import org.joymutlu.joyfulconverter.service.ConversionService;
import org.joymutlu.joyfulconverter.util.AlertUtils;

import static java.util.stream.Collectors.toList;

public class MainController implements Initializable {

    // --- FXML Elements ---
    @FXML private VBox mainContainer;
    @FXML private TextField inputPathField;
    @FXML private Button browseInputFileButton;
    @FXML private Button browseInputFolderButton;
    @FXML private TextField outputDirectoryField;
    @FXML private Button browseOutputDirectoryButton;
    @FXML private ChoiceBox<String> outputFormatChoiceBox;
    @FXML private CheckBox preserveQualityCheckbox; // Renamed in thought process, but FXML uses this ID
    @FXML private CheckBox replaceOriginalCheckbox;
    @FXML private Button shuffleButton;
    @FXML private Button normalizeButton;
    @FXML private Button convertButton;

    // Progress UI
    @FXML private GridPane progressGridPane;
    @FXML private ProgressBar overallProgressBar;
    @FXML private Label overallStatusLabel;
    @FXML private Label currentDirectoryProgressLabel;
    @FXML private Label currentDirectoryStatusLabel;
    @FXML private Label currentFileProgressLabel;
    @FXML private ProgressBar currentFileProgressBar;
    @FXML private Label currentFileStatusLabel;

    // --- Properties and Services ---
    private final StringProperty inputPathProperty = new SimpleStringProperty("");
    private final StringProperty outputDirectoryProperty = new SimpleStringProperty("");
    private ConversionService conversionService;
    private Task<Void> conversionTask;

    private static File lastSelectedInputDirectory = null;
    private static File lastSelectedOutputDirectory = null;

    private File inputSourceFileOrDir;
    private File outputDirectory;

    // Regular expression to match file name pattern: XXXX.YY. ZZZZ - NNN.fff
    private static final Pattern VIDEO_NAME_PATTERN =
            Pattern.compile("(\\d{4})\\.(\\d{2})\\. (\\d{4}) - (.+)\\.(.+)");

    // Regular expression to match file name patterns
    private static final Pattern PATTERN_1 = Pattern.compile("(\\d{4})\\.(\\d{2})\\. (\\d{4}) - (.+)\\.(.+)"); // XXXX.YY. ZZZZ - NNN.fff
    private static final Pattern PATTERN_2 = Pattern.compile("(\\d{4}) год - (.+)\\.(.+)"); // ZZZZ год - NNN.fff
    private static final Pattern PATTERN_3 = Pattern.compile("(?:\\[(\\d+)\\] )?(.+) \\((\\d{4})\\)\\.(.+)"); // [somenumber] NNN (ZZZZ).fff
    private static final Pattern PATTERN_4 = Pattern.compile("(\\d+) - (.+)\\.(.+)"); // ZZZZ - NNN.fff
    private static final Pattern DIRECTORY_PATTERN = Pattern.compile("(.+) год - (.+)"); // ZZZZ+ год - NNN

    /**
     * Reorganizes files from a directory structure into a single directory with a standardized naming pattern.
     * - Files matching XXXX.YYY. ZZZZ - NNN.fff are kept as is
     * - Files matching ZZZZ год - NNN.fff are renamed to standard pattern
     * - Files matching [somenumber] NNN (ZZZZ).fff are renamed to standard pattern
     * - Files in subdirectories are grouped together and moved to the main directory
     * - Each file in the root directory gets its own unique group
     *
     * @param directoryPath Path to the directory containing video files and subdirectories
     * @return true if reorganization was successful, false otherwise
     */
    public static boolean reorganizeVideos(String directoryPath) {
        File rootDirectory = new File(directoryPath);

        // Check if directory exists and is indeed a directory
        if (!rootDirectory.exists() || !rootDirectory.isDirectory()) {
            System.err.println("Error: The specified path is not a valid directory: " + directoryPath);
            return false;
        }

        // Generate a new unique group number counter
        int groupNumberCounter = 1;
        Map<File, String> directoryToGroupNumber = new HashMap<>();

        // First collect all subdirectories to assign group numbers
        for (File file : rootDirectory.listFiles()) {
            if (file.isDirectory()) {
                directoryToGroupNumber.put(file, String.format("%04d", groupNumberCounter++));
            }
        }

        // Process files in all subdirectories first
        try {
            // Process subdirectories first
            for (File file : rootDirectory.listFiles()) {
                if (file.isDirectory()) {
                    processDirectory(file, rootDirectory, directoryToGroupNumber);
                }
            }

            // Then process root files individually, each getting its own group
            List<VideoFileInfo> rootFiles = new ArrayList<>();
            for (File file : rootDirectory.listFiles()) {
                if (file.isFile()) {
                    VideoFileInfo fileInfo = extractVideoInfo(file, null);
                    if (fileInfo != null) {
                        rootFiles.add(fileInfo);
                    }
                }
            }

            // Process each root file separately, giving it its own group
            for (VideoFileInfo fileInfo : rootFiles) {
                // Skip files that are already in the correct format
                if (PATTERN_1.matcher(fileInfo.getFile().getName()).matches()) {
                    System.out.println("Skipping already correctly named file: " + fileInfo.getFile().getName());
                    continue;
                }

                // Create a unique group for this file
                String groupNumber = String.format("%04d", groupNumberCounter++);
                String unitNumber = "01"; // Always 01 since it's the only file in its group

                // Create the new file name according to the standard pattern
                String newFileName = groupNumber + "." + unitNumber + ". " +
                        fileInfo.getYear() + " - " +
                        fileInfo.getName() + "." +
                        fileInfo.getFormat();

                File destinationFile = new File(rootDirectory, newFileName);

                // Check if destination file already exists
                if (destinationFile.exists()) {
                    System.err.println("Error: Cannot rename " + fileInfo.getFile().getName() +
                            " to " + newFileName +
                            " because destination file already exists.");
                    continue;
                }

                // Rename the file
                try {
                    Files.move(fileInfo.getFile().toPath(), destinationFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Renamed: " + fileInfo.getFile().getName() +
                            " -> " + newFileName);
                } catch (Exception e) {
                    System.err.println("Error renaming file " + fileInfo.getFile().getName() +
                            ": " + e.getMessage());
                }
            }

            System.out.println("File reorganization completed successfully.");
            AlertUtils.showInformation("Normalization result", "Files reorganization and renaming finished successfully");

            return true;
        } catch (Exception e) {
            System.err.println("Error during file reorganization: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Process a directory and its files, renaming and moving files to the root directory
     */
    private static void processDirectory(File currentDir, File rootDir, Map<File, String> directoryToGroupNumber)
            throws Exception {

        File[] files = currentDir.listFiles();
        if (files == null) {
            return;
        }

        List<VideoFileInfo> videoFiles = new ArrayList<>();

        // First collect all video files in this directory
        for (File file : files) {
            if (file.isFile()) {
                VideoFileInfo fileInfo = extractVideoInfo(file, currentDir);
                if (fileInfo != null) {
                    videoFiles.add(fileInfo);
                }
            } else if (file.isDirectory()) {
                // Process subdirectories recursively
                processDirectory(file, rootDir, directoryToGroupNumber);
            }
        }

        // If no video files in this directory, return
        if (videoFiles.isEmpty()) {
            return;
        }

        // Reverse them for saving order
//        Collections.reverse(videoFiles);

        // Get the group number for this directory
        String groupNumber = directoryToGroupNumber.get(currentDir);

        // Rename and move each file to the root directory, maintaining original order
        // (we don't need to sort here, as we'll use the original order from the directory)
        for (int i = 0; i < videoFiles.size(); i++) {
            VideoFileInfo fileInfo = videoFiles.get(i);
            String unitNumber = String.format("%02d", i + 1); // Start from 1 for each directory

            // Create the new file name according to the standard pattern
            String newFileName = groupNumber + "." + unitNumber + ". " +
                    fileInfo.getYear() + " - " +
                    fileInfo.getName() + "." +
                    fileInfo.getFormat();

            File destinationFile = new File(rootDir, newFileName);

            // Check if destination file already exists
            if (destinationFile.exists()) {
                System.err.println("Error: Cannot rename " + fileInfo.getFile().getName() +
                        " to " + newFileName +
                        " because destination file already exists.");
                continue;
            }

            // Move and rename the file
            try {
                Files.move(fileInfo.getFile().toPath(), destinationFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Moved and renamed: " + fileInfo.getFile().getPath() +
                        " -> " + destinationFile.getPath());
            } catch (Exception e) {
                System.err.println("Error moving file " + fileInfo.getFile().getPath() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Extracts video information from a file based on its name pattern
     */
    private static VideoFileInfo extractVideoInfo(File file, File serialDir) {
        String fileName = file.getName();
        String fileSerialName = "";
        String directoryYear = "";
        if (serialDir != null) {
            final Matcher matcher = DIRECTORY_PATTERN.matcher(serialDir.getName());
            if (matcher.matches()) {
                directoryYear = matcher.group(1);
                fileSerialName = matcher.group(2) + ". ";
            }
        }

        // Try pattern 1: XXXX.YYY. ZZZZ - NNN.fff
        Matcher matcher1 = PATTERN_1.matcher(fileName);
        if (matcher1.matches()) {
            String year = matcher1.group(3);
            String name = matcher1.group(4);
            String format = matcher1.group(5);
            return new VideoFileInfo(file, year, fileSerialName + name, format);
        }

        // Try pattern 2: ZZZZ год - NNN.fff
        Matcher matcher2 = PATTERN_2.matcher(fileName);
        if (matcher2.matches()) {
            String year = matcher2.group(1);
            String name = matcher2.group(2);
            String format = matcher2.group(3);
            return new VideoFileInfo(file, year, fileSerialName + name, format);
        }

        // Try pattern 3: [somenumber] NNN (ZZZZ).fff
        Matcher matcher3 = PATTERN_3.matcher(fileName);
        if (matcher3.matches()) {
            String year = matcher3.group(3);
            String name = matcher3.group(2);
            String format = matcher3.group(4);
            return new VideoFileInfo(file, year, fileSerialName + name, format);
        }

        // Try pattern 4: ZZZ - NNN.fff
        Matcher matcher4 = PATTERN_4.matcher(fileName);
        if (matcher4.matches()) {
            String year = directoryYear;
            String name = matcher4.group(2);
            String format = matcher4.group(3);
            return new VideoFileInfo(file, year, fileSerialName + name, format);
        }

        // If no pattern matches, return null
        System.out.println("Skipping file with non-matching pattern: " + fileName);
        return null;
    }

    /**
     * Helper class to store video file information
     */
    private static class VideoFileInfo {
        private File file;
        private String year;
        private String name;
        private String format;

        public VideoFileInfo(File file, String year, String name, String format) {
            this.file = file;
            this.year = year;
            this.name = name;
            this.format = format;
        }

        public File getFile() { return file; }
        public String getYear() { return year; }
        public String getName() { return name; }
        public String getFormat() { return format; }
    }

    private boolean isInputFolderMode = false; // To distinguish between single file and folder mode for UI logic

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        conversionService = new ConversionService();

        inputPathField.textProperty().bind(inputPathProperty);
        outputDirectoryField.textProperty().bind(outputDirectoryProperty);

        outputFormatChoiceBox.setItems(FXCollections.observableArrayList("mp4", "mkv"));
        outputFormatChoiceBox.setValue("mp4");

        // Set "Preserve original quality" to be selected by default
        preserveQualityCheckbox.setSelected(true);
        replaceOriginalCheckbox.setSelected(false);

        setupButtonHandlers();
        setupInputPathListener();

        updateUIState();
    }

    private void setupButtonHandlers() {
        browseInputFileButton.setOnAction(event -> browseForInputFile());
        browseInputFolderButton.setOnAction(event -> browseForInputFolder());
        browseOutputDirectoryButton.setOnAction(event -> browseForOutputDirectory());
        shuffleButton.setOnAction(event -> shuffleContent());
        normalizeButton.setOnAction(event -> startRenaming());
        convertButton.setOnAction(event -> startConversion());
    }

    private void startRenaming() {
        if (prepareIOPaths() == PreparationStatus.FAILED) {
            return;
        }
        reorganizeVideos(inputPathProperty.get());
    }


    /**
     * Renames all video files in the specified directory according to requirements:
     * - Year, name, and format remain the same
     * - Video unit group numbers are randomly shuffled
     * - Unit numbers inside each group remain the same
     * */
    private void shuffleContent() {

        if (prepareIOPaths() == PreparationStatus.FAILED) {
            return;
        }
        if (!isInputFolderMode || inputSourceFileOrDir.isFile()) {
                AlertUtils.showError("Input Error", "Choose folder for shuffling.");
                return;
        }
        WalkResult walkResult = walkInputDirectory();
        if (walkResult.isFailure()) {
            AlertUtils.showError(walkResult.info().title(), walkResult.info().message());
            return;
        }
        if (walkResult.status() == WalkResultStatus.INFO) {
            AlertUtils.showInformation(walkResult.info().title(), walkResult.info().message());
        }
        final List<File> filesToProcess = List.copyOf(walkResult.filesForProcess());

        List<String> groupNumbers = new ArrayList<>();
        Map<String, List<File>> groupToFiles = new HashMap<>();

        for (File file : filesToProcess) {
            if (file.isFile()) {
                String fileName = file.getName();
                Matcher matcher = VIDEO_NAME_PATTERN.matcher(fileName);

                if (matcher.matches()) {
                    String groupNumber = matcher.group(1);

                    if (!groupToFiles.containsKey(groupNumber)) {
                        groupNumbers.add(groupNumber);
                        groupToFiles.put(groupNumber, new ArrayList<>());
                    }

                    groupToFiles.get(groupNumber).add(file);
                } else {
                    System.out.println("Skipping file with non-matching pattern: " + fileName);
                }
            }
        }
        if (groupNumbers.isEmpty()) {
            System.out.println("No files with the required pattern found in the directory.");
            return;
        }
        List<String> shuffledGroupNumbers = new ArrayList<>(groupNumbers);
        long seed = System.currentTimeMillis();
        Collections.shuffle(shuffledGroupNumbers, new Random(seed));

        Map<String, String> groupMapping = new HashMap<>();
        for (int i = 0; i < groupNumbers.size(); i++) {
            groupMapping.put(groupNumbers.get(i), shuffledGroupNumbers.get(i));
        }

        // Rename files
        for (String originalGroup : groupNumbers) {
            String newGroup = groupMapping.get(originalGroup);

            for (File file : groupToFiles.get(originalGroup)) {
                String fileName = file.getName();
                Matcher matcher = VIDEO_NAME_PATTERN.matcher(fileName);

                if (matcher.matches()) {
                    String unitNumber = matcher.group(2);
                    String year = matcher.group(3);
                    String name = matcher.group(4);
                    String format = matcher.group(5);

                    String newFileName = newGroup + "." + unitNumber + ". " + year + " - " + name + "." + format;
                    File newFile = new File(inputSourceFileOrDir, newFileName);

                    // Check if destination file already exists (unlikely with proper shuffling but a good precaution)
                    if (newFile.exists()) {
                        System.out.println("Warning: Will not be renamed " + fileName + " to " + newFileName + " because destination is the same.");
                        continue;
                    }

                    if (!file.renameTo(newFile)) {
                        System.err.println("Error: Failed to rename " + fileName + " to " + newFileName);
                    } else {
                        System.out.println("Renamed: " + fileName + " -> " + newFileName);
                    }
                }
            }
        }
        AlertUtils.showInformation("Shuffle result", "Shuffling finished successfully");
    }

    private void setupInputPathListener() {
        inputPathProperty.addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.isEmpty() && outputDirectoryProperty.get().isEmpty()) {
                File input = new File(newValue);
                if (input.exists()) {
                    File parentDir = input.isFile() ? input.getParentFile() : input;
                    if (parentDir != null) {
                        String suggestedName = input.isFile() ? "ConvertedVideos" : "Converted_" + parentDir.getName();
                        File baseDirForOutput = parentDir.getParentFile() != null ? parentDir.getParentFile() : parentDir;
                        File suggestedOutputDir = new File(baseDirForOutput, suggestedName);
                        outputDirectoryProperty.set(suggestedOutputDir.getAbsolutePath());
                    }
                }
            }
            updateUIState();
        });
        outputDirectoryProperty.addListener((obs, ov, nv) -> updateUIState());
    }


    private void updateUIState() {
        boolean hasInput = !inputPathProperty.get().isEmpty();
        boolean hasOutput = !outputDirectoryProperty.get().isEmpty();
        boolean isCurrentlyConverting = conversionTask != null && conversionTask.isRunning();

        browseInputFileButton.setDisable(isCurrentlyConverting);
        browseInputFolderButton.setDisable(isCurrentlyConverting);
        browseOutputDirectoryButton.setDisable(isCurrentlyConverting);
        shuffleButton.setDisable(!hasInput);
        normalizeButton.setDisable(!hasInput);
        convertButton.setDisable(isCurrentlyConverting || !hasInput || !hasOutput);
        outputFormatChoiceBox.setDisable(isCurrentlyConverting);
        preserveQualityCheckbox.setDisable(isCurrentlyConverting);
        replaceOriginalCheckbox.setDisable(isCurrentlyConverting);

        if (progressGridPane != null) {
            progressGridPane.setVisible(isCurrentlyConverting);
        }

        if (!isCurrentlyConverting) {
            resetProgressLabels();
            if (overallStatusLabel != null) { // Ensure overallStatusLabel is not null
                if (!hasInput) {
                    overallStatusLabel.setText("Select an AVI file or folder to convert.");
                } else if (!hasOutput) {
                    overallStatusLabel.setText("Specify an output directory.");
                } else {
                    overallStatusLabel.setText("Ready to convert.");
                }
                // Ensure overallStatusLabel is visible when not converting
                overallStatusLabel.setVisible(true);
            }
        } else {
            if(overallStatusLabel != null) overallStatusLabel.setVisible(true);
        }
    }

    private void resetProgressLabels() {
        if (overallProgressBar != null && !overallProgressBar.progressProperty().isBound()) {
            overallProgressBar.setProgress(0);
        }
        if (currentFileProgressBar != null) {
            currentFileProgressBar.setProgress(0);
        }

        if (overallStatusLabel != null) overallStatusLabel.setText("Ready."); // Default text when resetting

        // These labels are inside progressGridPane, their visibility is tied to it
        // or explicitly managed at the start of conversion.
        if (currentDirectoryProgressLabel != null) currentDirectoryProgressLabel.setVisible(false);
        if (currentDirectoryStatusLabel != null) {
            currentDirectoryStatusLabel.setText("");
            currentDirectoryStatusLabel.setVisible(false);
        }

        if (currentFileProgressLabel != null) currentFileProgressLabel.setVisible(false);
        if (currentFileStatusLabel != null) {
            currentFileStatusLabel.setText("");
            currentFileStatusLabel.setVisible(false);
        }
    }


    private void browseForInputFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select AVI Video File");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("AVI Video Files", "*.avi"));
        if (lastSelectedInputDirectory != null && lastSelectedInputDirectory.exists()) {
            fileChooser.setInitialDirectory(lastSelectedInputDirectory);
        }

        Stage stage = (Stage) mainContainer.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            inputPathProperty.set(selectedFile.getAbsolutePath());
            isInputFolderMode = false; // Set mode
            lastSelectedInputDirectory = selectedFile.getParentFile();
            if (outputDirectoryProperty.get().isEmpty() && selectedFile.getParentFile() != null) {
                outputDirectoryProperty.set(selectedFile.getParentFile().getAbsolutePath());
            }
        }
        updateUIState();
    }

    private void browseForInputFolder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Input Folder with AVI Files");
        if (lastSelectedInputDirectory != null && lastSelectedInputDirectory.exists()) {
            directoryChooser.setInitialDirectory(lastSelectedInputDirectory);
        }

        Stage stage = (Stage) mainContainer.getScene().getWindow();
        File selectedFolder = directoryChooser.showDialog(stage);

        if (selectedFolder != null) {
            inputPathProperty.set(selectedFolder.getAbsolutePath());
            isInputFolderMode = true; // Set mode
            lastSelectedInputDirectory = selectedFolder;
            if (outputDirectoryProperty.get().isEmpty()) {
                File parent = selectedFolder.getParentFile();
                if (parent == null) parent = selectedFolder;
                outputDirectoryProperty.set(new File(parent, "Converted_" + selectedFolder.getName()).getAbsolutePath());
            }
        }
        updateUIState();
    }

    private void browseForOutputDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Output Directory");
        if (lastSelectedOutputDirectory != null && lastSelectedOutputDirectory.exists()) {
            directoryChooser.setInitialDirectory(lastSelectedOutputDirectory);
        } else if (lastSelectedInputDirectory != null && lastSelectedInputDirectory.exists()){
            File suggestedParent = lastSelectedInputDirectory.isDirectory() ? lastSelectedInputDirectory : lastSelectedInputDirectory.getParentFile();
            if (suggestedParent == null && lastSelectedInputDirectory.isFile()) suggestedParent = lastSelectedInputDirectory.getParentFile();
            if (suggestedParent == null) suggestedParent = new File(System.getProperty("user.home"));
            directoryChooser.setInitialDirectory(suggestedParent);
        } else {
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }


        Stage stage = (Stage) mainContainer.getScene().getWindow();
        File selectedFolder = directoryChooser.showDialog(stage);

        if (selectedFolder != null) {
            outputDirectoryProperty.set(selectedFolder.getAbsolutePath());
            lastSelectedOutputDirectory = selectedFolder;
        }
        updateUIState();
    }

    private void startConversion() {
        boolean tryStreamCopy = preserveQualityCheckbox.isSelected(); // This now means "try to stream copy"
        boolean shouldReplaceOriginal = replaceOriginalCheckbox.isSelected();
        String outputFormat = outputFormatChoiceBox.getValue();
        if (prepareIOPaths() == PreparationStatus.FAILED) {
            return;
        }

        List<File> filesForProcess = new ArrayList<>();
        if (!isInputFolderMode && inputSourceFileOrDir.isFile()) {
            if (inputSourceFileOrDir.getName().toLowerCase().endsWith(".avi")) {
                filesForProcess.add(inputSourceFileOrDir);
            } else {
                AlertUtils.showError("Input Error", "Selected file is not an AVI file.");
                return;
            }
        } else if (isInputFolderMode && inputSourceFileOrDir.isDirectory()) {
            WalkResult walkResult = walkInputDirectory(file -> file.isFile() && file.getName().toLowerCase().endsWith(".avi"));
            if (walkResult.isFailure()) {
                AlertUtils.showError(walkResult.info().title(), walkResult.info().message());
                return;
            }
            if (walkResult.status() == WalkResultStatus.INFO) {
                AlertUtils.showInformation(walkResult.info().title(), walkResult.info().message());
            }
            filesForProcess = walkResult.filesForProcess();

        } else {
            AlertUtils.showError("Input Error", "Invalid input source selection.");
            return;
        }

        final List<File> filesToProcess = List.copyOf(filesForProcess);
        final int totalFiles = filesToProcess.size();

        AtomicInteger processedFilesCount = new AtomicInteger(0);
        AtomicInteger successfulConversions = new AtomicInteger(0);

        AtomicInteger remuxMp4Count = new AtomicInteger(0);
        AtomicInteger remuxMkvCount = new AtomicInteger(0);
        AtomicInteger reEncodeCount = new AtomicInteger(0);
        List<String> reEncodedFiles = new CopyOnWriteArrayList<>();
        AtomicInteger failedConversions = new AtomicInteger(0);

        conversionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> {
                    if (progressGridPane != null) progressGridPane.setVisible(true);
                    if (overallProgressBar != null) overallProgressBar.setVisible(true);
                    if (overallStatusLabel != null) overallStatusLabel.setVisible(true);

                    boolean showFolderSpecifics = totalFiles > 1 && isInputFolderMode;
                    if(currentDirectoryProgressLabel != null) currentDirectoryProgressLabel.setVisible(showFolderSpecifics);
                    if(currentDirectoryStatusLabel != null) currentDirectoryStatusLabel.setVisible(showFolderSpecifics);
                    if(currentFileProgressLabel != null) currentFileProgressLabel.setVisible(true);
                    if(currentFileProgressBar != null) currentFileProgressBar.setVisible(true);
                    if(currentFileStatusLabel != null) currentFileStatusLabel.setVisible(true);

                    overallStatusLabel.setText(String.format("Overall: Preparing... (0/%d)", totalFiles));
                });

                for (File inputFile : filesToProcess) {
                    if (isCancelled()) {
                        updateMessage("Conversion cancelled.");
                        break;
                    }

                    int currentFileNum = processedFilesCount.incrementAndGet();
                    String currentFileName = inputFile.getName();

                    Path relativeInputPath;
                    if (isInputFolderMode) {
                        relativeInputPath = inputSourceFileOrDir.toPath().relativize(inputFile.toPath());
                    } else {
                        relativeInputPath = Path.of(inputFile.getName());
                    }

                    // Initially set output file with chosen format
                    String outputFileName = relativeInputPath.toString().replaceAll("(?i)\\.avi$", "." + outputFormat);
                    Path outputPath = Path.of(outputDirectoryProperty.get(), outputFileName);
                    Files.createDirectories(outputPath.getParent());

                    final String finalCurrentDirDisplay = inputFile.getParentFile().getCanonicalPath();
                    Platform.runLater(() -> {
                        overallStatusLabel.setText(String.format("Overall: Processing file %d of %d. Quality loss: %d. Failures: %d",
                                currentFileNum, totalFiles, reEncodeCount.get(), failedConversions.get()));
                        if (totalFiles > 1 && isInputFolderMode) {
                            currentDirectoryStatusLabel.setText(finalCurrentDirDisplay);
                            currentDirectoryProgressLabel.setVisible(true);
                            currentDirectoryStatusLabel.setVisible(true);
                        } else {
                            if(currentDirectoryProgressLabel != null) currentDirectoryProgressLabel.setVisible(false);
                            if(currentDirectoryStatusLabel != null) currentDirectoryStatusLabel.setVisible(false);
                        }
                        currentFileStatusLabel.setText("Converting: " + currentFileName);
                        currentFileProgressBar.setProgress(0);
                        currentFileProgressLabel.setVisible(true);
                        currentFileProgressBar.setVisible(true);
                        currentFileStatusLabel.setVisible(true);
                    });
                    updateProgress(currentFileNum -1, totalFiles);

                    try {
                        ConversionResultStatus status = conversionService.convertVideo(
                                inputFile.getAbsolutePath(),
                                outputPath.toString(),
                                outputFormat,
                                tryStreamCopy, // Pass the correct flag
                                (progress) -> {
                                    Platform.runLater(() -> {
                                        currentFileProgressBar.setProgress(progress / 100.0);
                                        currentFileStatusLabel.setText(String.format("Converting: %s (%.1f%%)", currentFileName, progress));
                                    });
                                    updateMessage(String.format("File %d/%d: %s - %.1f%%", currentFileNum, totalFiles, currentFileName, progress));
                                }
                        );

                        // Check if the output file exists with the expected format
                        File outputFile = outputPath.toFile();
                        if (!outputFile.exists()) {
                            // If expected output file doesn't exist, check if MKV fallback was created
                            if ("mp4".equalsIgnoreCase(outputFormat)) {
                                String mkvPath = outputPath.toString().replaceAll("(?i)\\.mp4$", ".mkv");
                                File mkvFile = new File(mkvPath);
                                if (mkvFile.exists()) {
                                    // MKV fallback was created
                                    Platform.runLater(() -> {
                                        currentFileStatusLabel.setText("Completed with format change to MKV: " + mkvFile.getName());
                                    });
                                    System.out.println("Fallback to MKV was used for: " + inputFile.getName());
                                }
                            }
                        }

                        Platform.runLater(() -> currentFileStatusLabel.setText("Completed: " + currentFileName));

                        switch (status) {
                            case REMUX_MP4_OK:
                                remuxMp4Count.incrementAndGet();
                                successfulConversions.incrementAndGet();
                                Platform.runLater(() -> currentFileStatusLabel.setText("Completed (remuxed to MP4): " + currentFileName));
                                break;
                            case REMUX_MKV_OK:
                                remuxMkvCount.incrementAndGet();
                                successfulConversions.incrementAndGet();
                                Platform.runLater(() -> currentFileStatusLabel.setText("Completed (remuxed to MKV): " + currentFileName));
                                break;
                            case REENCODE_OK:
                                reEncodeCount.incrementAndGet();
                                successfulConversions.incrementAndGet();
                                reEncodedFiles.add(inputFile.getName()); // Collect names
                                Platform.runLater(() -> currentFileStatusLabel.setText("Completed (re-encoded): " + currentFileName));
                                break;
                            case FAILED:
                                // This case might be handled by the catch block below,
                                // but good to have for explicit failures returned by convertVideo
                                failedConversions.incrementAndGet();
                                Platform.runLater(() -> currentFileStatusLabel.setText("Failed: " + currentFileName));
                                break;
//                        successfulConversions.incrementAndGet();
                        }
                        if (shouldReplaceOriginal) {
                            try {
                                Files.deleteIfExists(inputFile.toPath());
                                System.out.println("Replaced (deleted) original file: " + inputFile.getAbsolutePath());
                            } catch (IOException e) {
                                System.err.println("Failed to delete original file " + inputFile.getAbsolutePath() + ": " + e.getMessage());
                                Platform.runLater(() -> AlertUtils.showWarning("Delete Failed", "Could not delete original file: " + inputFile.getName() + "\n" + e.getMessage()));
                            }
                        }
                    } catch (Exception e) {
                        failedConversions.incrementAndGet();
                        System.err.println("Failed to convert " + currentFileName + ": " + e.getMessage());
                        final String exceptionMessage = e.getMessage(); // Effectively final
                        Platform.runLater(() -> {
                            currentFileStatusLabel.setText("Failed: " + currentFileName);
                            overallStatusLabel.setText(String.format("Overall: Processing file %d of %d. Quality loss: %d. Failures: %d",
                                    currentFileNum, totalFiles, reEncodeCount.get(), failedConversions.get()));
                            AlertUtils.showWarning("Conversion Failed for File", "Could not convert: " + currentFileName + "\nReason: " + exceptionMessage);
                        });
                    }
                    updateProgress(currentFileNum, totalFiles);
                }
                return null;
            }
        };

        conversionTask.setOnSucceeded(event -> handleConversionCompletion(
                totalFiles,
                successfulConversions.get(),
                failedConversions.get(),
                remuxMp4Count,
                remuxMkvCount,
                reEncodeCount,
                reEncodedFiles));
        conversionTask.setOnFailed(event -> handleConversionFailure(conversionTask.getException()));
        conversionTask.setOnCancelled(event -> handleConversionCancellation());

        overallProgressBar.progressProperty().bind(conversionTask.progressProperty());
        updateUIState();

        Thread thread = new Thread(conversionTask);
        thread.setDaemon(true);
        thread.start();
    }

    private WalkResult walkInputDirectory() {
        return walkInputDirectory(file -> true);
    }

    private WalkResult walkInputDirectory(Predicate<File> filter) {
        List<File> result;
        try (Stream<Path> walk = Files.walk(inputSourceFileOrDir.toPath())) {
            result = walk.map(Path::toFile)
                    .filter(filter)
                    .collect(toList());
        } catch (IOException e) {
            return WalkResult.ofFailure("Input Error", "Error reading input folder: " + e.getMessage());
        }
        if (result.isEmpty()) {
            return WalkResult.ofInfo("No Files", "No files for process found in the selected folder and its subdirectories.");
        }
        return WalkResult.ofSuccess(result);
    }

    private PreparationStatus prepareIOPaths() {
        String inputPathStr = inputPathProperty.get();
        String outputDirStr = outputDirectoryProperty.get();

        inputSourceFileOrDir = new File(inputPathStr); // Renamed for clarity
        outputDirectory = new File(outputDirStr);

        if (!inputSourceFileOrDir.exists()) {
            AlertUtils.showError("Input Error", "Input source not found: " + inputPathStr);
            return PreparationStatus.FAILED;
        }
        if (!outputDirectory.exists()) {
            try {
                Files.createDirectories(outputDirectory.toPath());
            } catch (IOException e) {
                AlertUtils.showError("Output Error", "Could not create output directory: " + outputDirStr + "\n" + e.getMessage());
                return PreparationStatus.FAILED;
            }
        }
        if (!outputDirectory.isDirectory()) {
            AlertUtils.showError("Output Error", "Output location must be a directory.");
            return PreparationStatus.FAILED;
        }
        return PreparationStatus.SUCCESSFUL;
    }

    private void handleConversionCompletion(
            int total,
            int succeeded,
            int failed,
            AtomicInteger remuxMp4Count,
            AtomicInteger remuxMkvCount,
            AtomicInteger reEncodeCount,
            List<String> reEncodedFiles) {
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("Batch conversion complete.\nSuccessfully converted: %d\nFailed: %d\nTotal: %d\n\n",
                succeeded, failed, total));
        stats.append(String.format("- Remuxed to MP4 (no fallback): %d\n", remuxMp4Count.get()));
        stats.append(String.format("- Remuxed to MKV (direct or fallback): %d\n", remuxMkvCount.get()));
        stats.append(String.format("- Re-encoded (quality loss): %d\n", reEncodeCount.get()));

        if (!reEncodedFiles.isEmpty()) {
            stats.append("\nFiles re-encoded (potential quality loss):\n");
            reEncodedFiles.forEach(name -> stats.append("  - ").append(name).append("\n"));
        }

        String finalSummary = stats.toString();

        if (total == 0) {
            finalSummary = "No files were processed.";
        } else if (total == 1) {
            finalSummary = succeeded == 1 ? "Video successfully converted." : "Video conversion failed.";
        }

        if (failed > 0 && succeeded == 0 && total > 0) {
            AlertUtils.showError("Conversion Result", finalSummary);
        } else if (failed > 0) {
            AlertUtils.showWarning("Conversion Result", finalSummary);
        } else if (total == 0) {
            AlertUtils.showInformation("Conversion Info", finalSummary);
        }
        else {
            AlertUtils.showInformation("Conversion Complete", finalSummary);
        }
        resetConversionState();
    }

    private void handleConversionFailure(Throwable exception) {
        String errorMsg = "An unknown error occurred during the conversion process.";
        if (exception != null) {
            errorMsg = exception.getMessage() != null ? exception.getMessage() : exception.toString();
            exception.printStackTrace();
        }
        AlertUtils.showError("Conversion Process Failed", "Error: " + errorMsg);
        resetConversionState();
    }

    private void handleConversionCancellation() {
        AlertUtils.showInformation("Cancelled", "Conversion process was cancelled.");
        resetConversionState();
    }


    private void resetConversionState() {
        Platform.runLater(() -> {
            if (overallProgressBar != null && overallProgressBar.progressProperty().isBound()) {
                overallProgressBar.progressProperty().unbind();
            }
            conversionTask = null;
            // This will call resetProgressLabels and set text to "Ready for next conversion"
            // and potentially hide the progressGridPane.
            updateUIState();
        });
    }
}