package org.joymutlu.joyfulconverter;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.joymutlu.joyfulconverter.service.ConversionService;
import org.joymutlu.joyfulconverter.util.AlertUtils;

public class MainController implements Initializable {

    @FXML
    private TextField inputFileField;

    @FXML
    private TextField outputFileField;

    @FXML
    private Button browseInputButton;

    @FXML
    private Button browseOutputButton;

    @FXML
    private Button convertButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label statusLabel;

    @FXML
    private VBox mainContainer;

    @FXML
    private CheckBox preserveQualityCheckbox;

    private final StringProperty inputFilePath = new SimpleStringProperty("");
    private final StringProperty outputFilePath = new SimpleStringProperty("");
    private ConversionService conversionService;
    private Task<Void> conversionTask;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Initialize the conversion service
        conversionService = new ConversionService();

        // Bind text fields to properties
        inputFileField.textProperty().bindBidirectional(inputFilePath);
        outputFileField.textProperty().bindBidirectional(outputFilePath);

        // Set default value for "preserve quality" option
        preserveQualityCheckbox.setSelected(true);

        // Setup event handlers
        setupButtonHandlers();

        // Initialize UI state
        updateUIState();
    }

    private void setupButtonHandlers() {
        browseInputButton.setOnAction(event -> browseForInputFile());
        browseOutputButton.setOnAction(event -> browseForOutputFile());
        convertButton.setOnAction(event -> startConversion());

        // Update output filename when input changes
        inputFilePath.addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty()) {
                File inputFile = new File(newValue);
                String filename = inputFile.getName();

                // Check if it's an AVI file
                if (filename.toLowerCase().endsWith(".avi")) {
                    // Generate MP4 output path in the same directory
                    String outputName = filename.substring(0, filename.length() - 4) + ".mp4";
                    outputFilePath.set(new File(inputFile.getParent(), outputName).getAbsolutePath());
                }
            }
        });
    }

    private void updateUIState() {
        boolean hasInputFile = !inputFilePath.get().isEmpty();
        boolean hasOutputFile = !outputFilePath.get().isEmpty();
        boolean isConverting = conversionTask != null && conversionTask.isRunning();

        // Enable/disable buttons based on state
        browseInputButton.setDisable(isConverting);
        browseOutputButton.setDisable(isConverting || !hasInputFile);
        convertButton.setDisable(isConverting || !hasInputFile || !hasOutputFile);

        // Show/hide progress indicators
        progressBar.setVisible(isConverting);
        statusLabel.setVisible(true);

        if (!isConverting) {
            if (!hasInputFile) {
                statusLabel.setText("Select an AVI file to convert");
            } else if (!hasOutputFile) {
                statusLabel.setText("Specify output MP4 file location");
            } else {
                statusLabel.setText("Ready to convert");
            }
        }
    }

    private void browseForInputFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select AVI Video File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("AVI Video Files", "*.avi")
        );

        // Get stage from any FXML control
        Stage stage = (Stage) mainContainer.getScene().getWindow();

        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            inputFilePath.set(selectedFile.getAbsolutePath());
            updateUIState();
        }
    }

    private void browseForOutputFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save MP4 Video File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("MP4 Video Files", "*.mp4")
        );

        // Get default filename from input file
        if (!inputFilePath.get().isEmpty()) {
            File inputFile = new File(inputFilePath.get());
            String defaultName = inputFile.getName().replaceAll("\\.avi$", ".mp4");
            fileChooser.setInitialFileName(defaultName);
            fileChooser.setInitialDirectory(inputFile.getParentFile());
        }

        // Get stage from any FXML control
        Stage stage = (Stage) mainContainer.getScene().getWindow();

        File selectedFile = fileChooser.showSaveDialog(stage);
        if (selectedFile != null) {
            outputFilePath.set(selectedFile.getAbsolutePath());
            updateUIState();
        }
    }

    private void startConversion() {
        // Verify files
        File inputFile = new File(inputFilePath.get());
        File outputFile = new File(outputFilePath.get());

        if (!inputFile.exists() || !inputFile.isFile()) {
            AlertUtils.showError("Input file not found",
                    "The selected input file does not exist or is not accessible.");
            return;
        }

        // Check if output file exists and confirm overwrite
        if (outputFile.exists()) {
            boolean confirmed = AlertUtils.showConfirmation(
                    "File exists",
                    "The output file already exists. Do you want to overwrite it?");

            if (!confirmed) {
                return;
            }
        }

        // Create and start the conversion task
        conversionTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Preparing to convert...");
                updateProgress(0, 100);

                // Start conversion with progress updates
                conversionService.convertAviToMp4(
                        inputFile.getAbsolutePath(),
                        outputFile.getAbsolutePath(),
                        preserveQualityCheckbox.isSelected(),
                        (progress) -> {
                            updateProgress(progress, 100);
                            updateMessage(String.format("Converting: %.1f%%", progress));
                        }
                );

                updateMessage("Conversion complete");
                updateProgress(100, 100);
                return null;
            }
        };

        // Handle task events
        conversionTask.setOnSucceeded(event -> {
            AlertUtils.showInformation(
                    "Conversion Complete",
                    "The video has been successfully converted to MP4 format."
            );
            resetConversionState();
        });

        conversionTask.setOnFailed(event -> {
            Throwable exception = conversionTask.getException();
            String errorMsg = exception != null ? exception.getMessage() : "Unknown error";

            AlertUtils.showError(
                    "Conversion Failed",
                    "Failed to convert video: " + errorMsg
            );
            resetConversionState();
        });

        // Bind progress
        progressBar.progressProperty().bind(conversionTask.progressProperty());
        statusLabel.textProperty().bind(conversionTask.messageProperty());

        // Update UI state
        convertButton.setDisable(true);
        browseInputButton.setDisable(true);
        browseOutputButton.setDisable(true);

        // Start conversion in background thread
        Thread thread = new Thread(conversionTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void resetConversionState() {
        Platform.runLater(() -> {
            statusLabel.textProperty().unbind();
            progressBar.progressProperty().unbind();
            statusLabel.setText("Ready for next conversion");
            conversionTask = null;
            updateUIState();
        });
    }
}