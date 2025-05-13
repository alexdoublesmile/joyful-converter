package org.joymutlu.joyfulconverter;

import java.util.Objects;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.joymutlu.joyfulconverter.util.AlertUtils;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        try {
            Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/MainView.fxml")));
            Scene scene = new Scene(root);
            primaryStage.setTitle("Joymutlu Tools");
            primaryStage.setScene(scene);

            // Set minimum size to prevent UI elements from being cut off
            // Adjusted min width and height for the new layout
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(750);


            // Add application icon
            try {
                primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/java-coffee-cup-logo.png"))));
            } catch (Exception e) {
                System.err.println("Could not load application icon: " + e.getMessage());
            }

            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            AlertUtils.showError("Application Error", "Could not start the application: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}