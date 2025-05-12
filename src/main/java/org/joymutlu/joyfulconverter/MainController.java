package org.joymutlu.joyfulconverter;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.joymutlu.joyfulconverter.service.ConversionService;
import org.joymutlu.joyfulconverter.util.AlertUtils;

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
        convertButton.setOnAction(event -> startConversion());
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
        String inputPathStr = inputPathProperty.get();
        String outputDirStr = outputDirectoryProperty.get();
        String outputFormat = outputFormatChoiceBox.getValue();
        boolean tryStreamCopy = preserveQualityCheckbox.isSelected(); // This now means "try to stream copy"
        boolean shouldReplaceOriginal = replaceOriginalCheckbox.isSelected();

        File inputSourceFileOrDir = new File(inputPathStr); // Renamed for clarity
        File outputDirectory = new File(outputDirStr);

        if (!inputSourceFileOrDir.exists()) {
            AlertUtils.showError("Input Error", "Input source not found: " + inputPathStr);
            return;
        }
        if (!outputDirectory.exists()) {
            try {
                Files.createDirectories(outputDirectory.toPath());
            } catch (IOException e) {
                AlertUtils.showError("Output Error", "Could not create output directory: " + outputDirStr + "\n" + e.getMessage());
                return;
            }
        }
        if (!outputDirectory.isDirectory()) {
            AlertUtils.showError("Output Error", "Output location must be a directory.");
            return;
        }

        List<File> collectedFilesToConvert = new ArrayList<>();
        if (!isInputFolderMode && inputSourceFileOrDir.isFile()) { // Use isInputFolderMode
            if (inputSourceFileOrDir.getName().toLowerCase().endsWith(".avi")) {
                collectedFilesToConvert.add(inputSourceFileOrDir);
            } else {
                AlertUtils.showError("Input Error", "Selected file is not an AVI file.");
                return;
            }
        } else if (isInputFolderMode && inputSourceFileOrDir.isDirectory()) { // Use isInputFolderMode
            try (Stream<Path> walk = Files.walk(inputSourceFileOrDir.toPath())) {
                collectedFilesToConvert = walk.map(Path::toFile)
                        .filter(file -> file.isFile() && file.getName().toLowerCase().endsWith(".avi"))
                        .collect(Collectors.toList());
            } catch (IOException e) {
                AlertUtils.showError("Input Error", "Error reading input folder: " + e.getMessage());
                return;
            }
            if (collectedFilesToConvert.isEmpty()) {
                AlertUtils.showInformation("No Files", "No AVI files found in the selected folder and its subdirectories.");
                return;
            }
        } else {
            AlertUtils.showError("Input Error", "Invalid input source selection.");
            return;
        }

        final List<File> filesToProcess = Collections.unmodifiableList(new ArrayList<>(collectedFilesToConvert));
        final int totalFiles = filesToProcess.size();

        AtomicInteger processedFilesCount = new AtomicInteger(0);
        AtomicInteger successfulConversions = new AtomicInteger(0);
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
                        relativeInputPath = Paths.get(inputFile.getName());
                    }

                    // Initially set output file with chosen format
                    String outputFileName = relativeInputPath.toString().replaceAll("(?i)\\.avi$", "." + outputFormat);
                    Path outputPath = Paths.get(outputDirStr, outputFileName);

                    // Make sure parent directories exist
                    Files.createDirectories(outputPath.getParent());

                    String currentDirDisplay;
                    if (!isInputFolderMode || inputFile.getParentFile().equals(inputSourceFileOrDir)) {
                        currentDirDisplay = !isInputFolderMode ? "Selected File" : "Root Input Folder";
                    } else {
                        currentDirDisplay = inputSourceFileOrDir.toPath().relativize(inputFile.getParentFile().toPath()).toString();
                    }

                    final String finalCurrentDirDisplay = currentDirDisplay; // Effectively final for lambda
                    Platform.runLater(() -> {
                        overallStatusLabel.setText(String.format("Overall: Processing file %d of %d. Failures: %d",
                                currentFileNum, totalFiles, failedConversions.get()));
                        if (totalFiles > 1 && isInputFolderMode) {
                            currentDirectoryStatusLabel.setText("In directory: " + finalCurrentDirDisplay);
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
                        conversionService.convertVideo(
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
                        successfulConversions.incrementAndGet();

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
                            overallStatusLabel.setText(String.format("Overall: Processing file %d of %d. Failures: %d",
                                    currentFileNum, totalFiles, failedConversions.get()));
                            AlertUtils.showWarning("Conversion Failed for File", "Could not convert: " + currentFileName + "\nReason: " + exceptionMessage);
                        });
                    }
                    updateProgress(currentFileNum, totalFiles);
                }
                return null;
            }
        };

        conversionTask.setOnSucceeded(event -> handleConversionCompletion(totalFiles, successfulConversions.get(), failedConversions.get()));
        conversionTask.setOnFailed(event -> handleConversionFailure(conversionTask.getException()));
        conversionTask.setOnCancelled(event -> handleConversionCancellation());

        overallProgressBar.progressProperty().bind(conversionTask.progressProperty());
        updateUIState();

        Thread thread = new Thread(conversionTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void handleConversionCompletion(int total, int succeeded, int failed) {
        String summary;
        if (total == 0) { // Should not happen if we check for empty list earlier
            summary = "No files were processed.";
        } else if (total > 1) {
            summary = String.format("Batch conversion complete.\nSuccessfully converted: %d\nFailed: %d\nTotal: %d",
                    succeeded, failed, total);
        } else { // total == 1
            summary = succeeded == 1 ? "Video successfully converted." : "Video conversion failed.";
        }

        if (failed > 0 && succeeded == 0 && total > 0) {
            AlertUtils.showError("Conversion Result", summary);
        } else if (failed > 0) {
            AlertUtils.showWarning("Conversion Result", summary);
        } else if (total == 0) {
            AlertUtils.showInformation("Conversion Info", summary);
        }
        else {
            AlertUtils.showInformation("Conversion Complete", summary);
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