package org.joymutlu.joyfulconverter;

import java.util.Objects;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        try {
            Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/fxml/MainView.fxml")));
            Scene scene = new Scene(root);
            primaryStage.setTitle("AVI to MP4 Converter");
            primaryStage.setScene(scene);

            // Set minimum size to prevent UI elements from being cut off
            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            // Add application icon
            try {
                primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/app_icon.png"))));
            } catch (Exception e) {
                System.out.println("Could not load application icon: " + e.getMessage());
            }

            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}