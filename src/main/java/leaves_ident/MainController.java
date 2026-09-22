package leaves_ident;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.canvas.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.animation.*;
import javafx.util.Duration;
import java.io.File;
import java.util.*;
import java.util.Arrays;


public class MainController {

    @FXML private BorderPane rootPane;
    @FXML private Label statusLabel;
    @FXML private Label leafCountLabel;
    @FXML private CheckBox showNumbersCheckBox;
    @FXML private HBox imageContainer;
    @FXML private TextField minSizeField;

    @FXML private Slider hueMinSlider;
    @FXML private Slider hueMaxSlider;
    @FXML private Slider saturationSlider;
    @FXML private Slider brightnessSlider;

    private double hueMin = 0;
    private double hueMax= 60;
    private double saturationMin = 0.2;
    private double brightnessMin = 0.15;


    private Image originalImage;
    private Image filteredImage;
    private WritableImage bwImage;
    private int imageWidth, imageHeight;
    private Canvas originalOverlayCanvas;
    private Canvas bwOverlayCanvas;
    private ImageView originalImageView;
    private ImageView bwImageView;
    private UnionFind uf;
    private List<LeafCluster> clusters;
    private Map<Integer, Color> clusterColors = new HashMap<>();
    private int minLeafSize = 20;
    private Timeline tspAnimation;
    private List<LeafCluster> tspPath;
    private int[] pixelToCluster;



    class LeafCluster {
        int number, size, minX, minY, maxX, maxY;
        List<Integer> pixels = new ArrayList<>();
        double centerX, centerY;

        void addPixel(int x, int y, int idx) {
            pixels.add(idx);
            if (size == 0) { minX = maxX = x; minY = maxY = y; }
            else {
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
            size++;
        }
        void calculateCenter() { centerX = (minX + maxX) / 2.0; centerY = (minY + maxY) / 2.0; }
        int getWidth() { return maxX - minX + 1; }
        int getHeight() { return maxY - minY + 1; }
        String getSizeInfo() { return size + " pixels"; }
    }

    @FXML
    public void initialize() {
        statusLabel.setText("Ready - Open an image");
        setupSliders();
    }



    @FXML
    private void handleApplyNoiseFilter() {
        try {
            minLeafSize = Integer.parseInt(minSizeField.getText());
            if (bwImage != null) handleFindLeaves();
        } catch (NumberFormatException e) {}
    }

    private void setupSliders() {
        hueMinSlider.valueProperty().addListener((obs, old, newVal) -> {
            hueMin = newVal.doubleValue();
            applyHSVFilterToOriginal();
        });
        hueMaxSlider.valueProperty().addListener((obs, old, newVal) -> {
            hueMax = newVal.doubleValue();
            applyHSVFilterToOriginal();
        });
        saturationSlider.valueProperty().addListener((obs, old, newVal) -> {
            saturationMin = newVal.doubleValue();
            applyHSVFilterToOriginal();
        });
        brightnessSlider.valueProperty().addListener((obs, old, newVal) -> {
            brightnessMin = newVal.doubleValue();
            applyHSVFilterToOriginal();
        });
    }

    @FXML
    private void handleApplyHSV() {
        if (originalImage != null) {
            applyHSVFilterToOriginal();
            statusLabel.setText(String.format("HSV: H[%.0f-%.0f] S>%.2f B>%.2f",
                    hueMin, hueMax, saturationMin, brightnessMin));
        }
    }

    private void applyHSVFilterToOriginal() {
        if (originalImage == null) return;

        WritableImage filtered = new WritableImage(imageWidth, imageHeight);
        PixelReader reader = originalImage.getPixelReader();
        PixelWriter writer = filtered.getPixelWriter();

        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                Color c = reader.getColor(x, y);
                double hue = c.getHue();
                double sat = c.getSaturation();
                double bright = c.getBrightness();

                boolean inHueRange = (hue >= hueMin && hue <= hueMax) ||
                        (hue >= 340 && hue <= 360);
                boolean inSatRange = sat > saturationMin;
                boolean inBrightRange = bright > brightnessMin;

                if (inHueRange && inSatRange && inBrightRange) {
                    writer.setColor(x, y, c);
                } else {
                    double gray = (c.getRed() + c.getGreen() + c.getBlue()) / 3.0;
                    writer.setColor(x, y, new Color(gray * 0.5, gray * 0.5, gray * 0.5, 1.0));
                }
            }
        }

        filteredImage = filtered;
        originalImageView.setImage(filteredImage);
    }


    @FXML
    private void handleOpenImage() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = fc.showOpenDialog(rootPane.getScene().getWindow());
        if (file == null) return;

        originalImage = new Image(file.toURI().toString(), 512, 512, false, true);
        imageWidth = (int) originalImage.getWidth();
        imageHeight = (int) originalImage.getHeight();

        displayImages();
        statusLabel.setText("Loaded: " + file.getName());
        clusters = null;
        leafCountLabel.setText("Leaves: 0");
        clusterColors.clear();
    }

    private void displayImages() {
        imageContainer.getChildren().clear();

        // Original image
        originalImageView = new ImageView(originalImage);
        originalImageView.setFitWidth(512);
        originalImageView.setFitHeight(512);
        originalImageView.setPickOnBounds(true);
        originalImageView.setOnMouseClicked(this::handleOriginalImageClick);

        originalOverlayCanvas = new Canvas(512, 512);
        originalOverlayCanvas.setMouseTransparent(true);

        StackPane originalStack = new StackPane(originalImageView, originalOverlayCanvas);
        VBox originalBox = new VBox(5, new Label("Original Image"), originalStack);
        originalBox.setAlignment(javafx.geometry.Pos.CENTER);

        // BW image
        bwImageView = new ImageView();
        bwImageView.setFitWidth(512);
        bwImageView.setFitHeight(512);
        bwImageView.setPickOnBounds(true);
        bwImageView.setOnMouseClicked(this::handleBWClick);

        bwOverlayCanvas = new Canvas(512, 512);
        bwOverlayCanvas.setMouseTransparent(true);

        StackPane bwStack = new StackPane(bwImageView, bwOverlayCanvas);
        VBox bwBox = new VBox(5, new Label("Black & White Image "), bwStack);
        bwBox.setAlignment(javafx.geometry.Pos.CENTER);

        imageContainer.getChildren().addAll(originalBox, bwBox);
    }



    @FXML
    private void handleConvertToBW() {
        if (originalImage == null) {
            showAlert("Error", "Open an image first");
            return;
        }

        // filteredImage if there is, otherwise originalImage
        Image source = (filteredImage != null) ? filteredImage : originalImage;

        bwImage = new WritableImage(imageWidth, imageHeight);
        PixelReader reader = source.getPixelReader();
        PixelWriter writer = bwImage.getPixelWriter();

        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                Color c = reader.getColor(x, y);

                // Brightness of the pixel
                double brightness = c.getBrightness();
                boolean isWhite = brightness > 0.3 && c.getSaturation() > 0.1;

                writer.setColor(x, y, isWhite ? Color.WHITE : Color.BLACK);
            }
        }
        bwImageView.setImage(bwImage);
        statusLabel.setText("Black & White done");
    }

    @FXML
    private void handleFindLeaves() {
        if (bwImage == null) {
            showAlert("Error", "Convert to B&W first");
            return;
        }

        int total = imageWidth * imageHeight;
        PixelReader reader = bwImage.getPixelReader();
        boolean[] isWhite = new boolean[total];

        // use getBrightness() instead of equals()
        for (int i = 0; i < total; i++) {
            Color c = reader.getColor(i % imageWidth, i / imageWidth);
            isWhite[i] = c.getBrightness() > 0.9;  // white pixel detection
        }

        uf = new UnionFind(total);
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int idx = y * imageWidth + x;
                if (!isWhite[idx]) continue;
                if (x + 1 < imageWidth && isWhite[y * imageWidth + x + 1])
                    uf.union(idx, y * imageWidth + x + 1);
                if (y + 1 < imageHeight && isWhite[(y + 1) * imageWidth + x])
                    uf.union(idx, (y + 1) * imageWidth + x);
            }
        }

        Map<Integer, LeafCluster> clusterMap = new HashMap<>();
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                int idx = y * imageWidth + x;
                if (!isWhite[idx]) continue;
                int root = uf.find(idx);
                clusterMap.computeIfAbsent(root, k -> new LeafCluster()).addPixel(x, y, idx);
            }
        }

        clusters = new ArrayList<>(clusterMap.values());
        clusters.removeIf(c -> c.size < minLeafSize);
        clusters.sort((a, b) -> b.size - a.size);
        for (int i = 0; i < clusters.size(); i++) {
            clusters.get(i).number = i + 1;
            clusters.get(i).calculateCenter();
        }

        //  CREATING FAST LOOKUP ARRAY
        pixelToCluster = new int[total];
        Arrays.fill(pixelToCluster, -1);
        for (int i = 0; i < clusters.size(); i++) {
            LeafCluster c = clusters.get(i);
            for (int idx : c.pixels) {
                pixelToCluster[idx] = i;  // store cluster index
            }
        }
        //

        leafCountLabel.setText("Leaves: " + clusters.size());
        drawRectangles();
        generateColors();
        statusLabel.setText("Found " + clusters.size() + " leaf clusters");
    }

    private void drawRectangles() {
        if (originalOverlayCanvas == null || clusters == null) return;
        GraphicsContext gc = originalOverlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, 512, 512);
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(2);
        gc.setFill(Color.BLUE);

        double scaleX = 512.0 / imageWidth;
        double scaleY = 512.0 / imageHeight;

        for (LeafCluster c : clusters) {
            double rx = c.minX * scaleX;
            double ry = c.minY * scaleY;
            double rw = c.getWidth() * scaleX;
            double rh = c.getHeight() * scaleY;
            gc.strokeRect(rx, ry, rw, rh);
            if (showNumbersCheckBox.isSelected()) {
                gc.fillText(String.valueOf(c.number), rx + 5, ry + 15);
            }
        }
    }

    private void generateColors() {
        Random rand = new Random(42);
        for (LeafCluster c : clusters) {
            clusterColors.put(c.number, Color.hsb(rand.nextDouble() * 360, 0.8, 0.8));
        }
    }

    private void handleBWClick(MouseEvent event) {
        System.out.println("=== BW CLICK DETECTED ===");
        System.out.println("Event source: " + event.getSource());
        System.out.println("Mouse: " + event.getX() + ", " + event.getY());
        if (clusters == null || clusters.isEmpty()) {
            showAlert("Info", "Find leaves first");
            return;
        }

        if (pixelToCluster == null) {
            statusLabel.setText("Error: pixel mapping not initialized");
            return;
        }

        double mouseX = event.getX();
        double mouseY = event.getY();

        if (mouseX < 0 || mouseX > bwImageView.getFitWidth() ||
                mouseY < 0 || mouseY > bwImageView.getFitHeight()) {
            return;
        }

        int pixelX = (int) (mouseX / 512.0 * imageWidth);
        int pixelY = (int) (mouseY / 512.0 * imageHeight);

        pixelX = Math.max(0, Math.min(pixelX, imageWidth - 1));
        pixelY = Math.max(0, Math.min(pixelY, imageHeight - 1));

        int idx = pixelY * imageWidth + pixelX;

        int clusterIndex = pixelToCluster[idx];
        if (clusterIndex == -1) {
            statusLabel.setText("No leaf cluster at this pixel");
            return;
        }

        LeafCluster cluster = clusters.get(clusterIndex);

        // Highlight first, then shows info
        highlightSingleCluster(cluster);
        showClusterInfo(cluster);
    }

    private void highlightSingleCluster(LeafCluster cluster) {
        // Restores original BW im
        if (bwImage != null && bwImageView != null) {
            bwImageView.setImage(bwImage);
        }

        // Draw on them
        bwOverlayCanvas.getGraphicsContext2D().clearRect(0, 0, 512, 512);

        double scaleX = 512.0 / imageWidth;
        double scaleY = 512.0 / imageHeight;

        GraphicsContext gc = bwOverlayCanvas.getGraphicsContext2D();
        gc.setFill(Color.RED);

        for (int idx : cluster.pixels) {
            int x = idx % imageWidth;
            int y = idx / imageWidth;
            gc.fillRect(x * scaleX, y * scaleY, scaleX, scaleY);
        }

        statusLabel.setText("Cluster #" + cluster.number + " (" + cluster.getSizeInfo() + ")");
    }

    private void showClusterInfo(LeafCluster cluster) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Cluster Information");
        alert.setHeaderText("Leaf/Cluster Number: " + cluster.number);
        alert.setContentText("Size: " + cluster.size + " pixels\n" +
                "Position: (" + cluster.minX + "," + cluster.minY + ") to (" +
                cluster.maxX + "," + cluster.maxY + ")\n" +
                "Width: " + cluster.getWidth() + " px, Height: " + cluster.getHeight() + " px");
        alert.showAndWait();
    }

    @FXML
    private void handleColorAllClusters() {
        if (bwImage == null || clusters == null || clusters.isEmpty()) {
            showAlert("Error", "Find leaves first");
            return;
        }

        // Image with original size
        WritableImage coloredBWImage = new WritableImage(imageWidth, imageHeight);
        PixelWriter writer = coloredBWImage.getPixelWriter();

        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                writer.setColor(x, y, Color.BLACK);
            }
        }

        // Draw color clusters
        for (LeafCluster c : clusters) {
            Color color = clusterColors.get(c.number);
            for (int idx : c.pixels) {
                int x = idx % imageWidth;
                int y = idx / imageWidth;
                writer.setColor(x, y, color);
            }
        }

        bwImageView.setImage(coloredBWImage);
        statusLabel.setText("All clusters colored");
    }


    @FXML
    private void handleColorSingleCluster() {
        if (clusters == null || clusters.isEmpty()) {
            showAlert("Error", "Find leaves first");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Select Cluster");
        dialog.setHeaderText("Color Single Cluster");
        dialog.setContentText("Enter cluster number (1-" + clusters.size() + "):");

        dialog.showAndWait().ifPresent(result -> {
            try {
                int number = Integer.parseInt(result);
                clusters.stream().filter(c -> c.number == number).findFirst().ifPresent(target -> {
                    highlightSingleCluster(target);
                    statusLabel.setText("Cluster #" + number + " - " + target.size + " pixels");
                });
            } catch (NumberFormatException e) {
                showAlert("Error", "Invalid number");
            }
        });
    }

    // TSP path
    @FXML
    private void handleRunTSP() {
        if (clusters == null || clusters.isEmpty()) {
            showAlert("Error", "Find leaves first");
            return;
        }

        if (tspAnimation != null) {
            tspAnimation.stop();
        }

        GraphicsContext gc = originalOverlayCanvas.getGraphicsContext2D();
        drawRectangles();

        // Nearest Neighbor TSP
        List<LeafCluster> unvisited = new ArrayList<>(clusters);
        tspPath = new ArrayList<>();
        LeafCluster current = clusters.get(0);
        tspPath.add(current);
        unvisited.remove(current);

        while (!unvisited.isEmpty()) {
            LeafCluster nearest = null;
            double minDist = Double.MAX_VALUE;
            for (LeafCluster c : unvisited) {
                double dx = current.centerX - c.centerX;
                double dy = current.centerY - c.centerY;
                double dist = dx*dx + dy*dy;
                if (dist < minDist) {
                    minDist = dist;
                    nearest = c;
                }
            }
            current = nearest;
            tspPath.add(current);
            unvisited.remove(current);
        }

        double scaleX = 512.0 / imageWidth;
        double scaleY = 512.0 / imageHeight;
        gc.setStroke(Color.RED);
        gc.setLineWidth(2);
        for (int i = 0; i < tspPath.size() - 1; i++) {
            LeafCluster from = tspPath.get(i);
            LeafCluster to = tspPath.get(i + 1);
            gc.strokeLine(from.centerX * scaleX, from.centerY * scaleY,
                    to.centerX * scaleX, to.centerY * scaleY);
        }

        animateTSPPath();
        statusLabel.setText("TSP path drawn from cluster #" + clusters.get(0).number);
    }

    private void animateTSPPath() {
        if (tspPath == null || tspPath.isEmpty()) return;

        tspAnimation = new Timeline();
        Duration frameDuration = Duration.seconds(5.0 / tspPath.size());

        for (int i = 0; i < tspPath.size(); i++) {
            final int index = i;
            KeyFrame kf = new KeyFrame(frameDuration.multiply(i), e -> {
                highlightClusterForTSP(tspPath.get(index));
            });
            tspAnimation.getKeyFrames().add(kf);
        }

        tspAnimation.setOnFinished(e -> statusLabel.setText("TSP animation complete"));
        tspAnimation.play();
    }

    private void highlightClusterForTSP(LeafCluster cluster) {
        GraphicsContext gc = originalOverlayCanvas.getGraphicsContext2D();
        double scaleX = 512.0 / imageWidth;
        double scaleY = 512.0 / imageHeight;
        double rx = cluster.minX * scaleX;
        double ry = cluster.minY * scaleY;
        double rw = cluster.getWidth() * scaleX;
        double rh = cluster.getHeight() * scaleY;

        gc.setStroke(Color.YELLOW);
        gc.setLineWidth(3);
        gc.strokeRect(rx, ry, rw, rh);

        PauseTransition pause = new PauseTransition(Duration.millis(150));
        pause.setOnFinished(e -> {
            drawRectangles();
            // redraw TSP lines
            if (tspPath != null && tspPath.size() > 1) {
                gc.setStroke(Color.RED);
                gc.setLineWidth(2);
                for (int i = 0; i < tspPath.size() - 1; i++) {
                    LeafCluster from = tspPath.get(i);
                    LeafCluster to = tspPath.get(i + 1);
                    gc.strokeLine(from.centerX * scaleX, from.centerY * scaleY,
                            to.centerX * scaleX, to.centerY * scaleY);
                }
            }
            if (showNumbersCheckBox.isSelected()) {
                gc.setFill(Color.BLUE);
                for (LeafCluster c : clusters) {
                    gc.fillText(String.valueOf(c.number), c.minX * scaleX + 5, c.minY * scaleY + 15);
                }
            }
        });
        pause.play();
    }

    //ToggleNumber
    @FXML
    private void handleToggleNumbers() {
        drawRectangles();
        if (tspPath != null && tspPath.size() > 1) {
            GraphicsContext gc = originalOverlayCanvas.getGraphicsContext2D();
            double scaleX = 512.0 / imageWidth;
            double scaleY = 512.0 / imageHeight;
            gc.setStroke(Color.RED);
            gc.setLineWidth(2);
            for (int i = 0; i < tspPath.size() - 1; i++) {
                LeafCluster from = tspPath.get(i);
                LeafCluster to = tspPath.get(i + 1);
                gc.strokeLine(from.centerX * scaleX, from.centerY * scaleY,
                        to.centerX * scaleX, to.centerY * scaleY);
            }
        }
    }

    @FXML
    private void handleOriginalImageClick(MouseEvent event) {
        if (clusters == null || clusters.isEmpty()) {
            statusLabel.setText("No clusters found. Run 'Find Leaves' first.");
            return;
        }

        if (pixelToCluster == null) {
            statusLabel.setText("Error: pixel mapping not initialized");
            return;
        }

        double mouseX = event.getX();
        double mouseY = event.getY();

        double imgViewWidth = originalImageView.getFitWidth();
        double imgViewHeight = originalImageView.getFitHeight();

        double scaleX = (double)imageWidth / imgViewWidth;
        double scaleY = (double)imageHeight / imgViewHeight;

        int pixelX = (int) (mouseX * scaleX);
        int pixelY = (int) (mouseY * scaleY);

        pixelX = Math.max(0, Math.min(pixelX, imageWidth - 1));
        pixelY = Math.max(0, Math.min(pixelY, imageHeight - 1));

        int idx = pixelY * imageWidth + pixelX;

        int clusterIndex = pixelToCluster[idx];
        if (clusterIndex == -1) {
            statusLabel.setText("No leaf cluster at this pixel");
            return;
        }

        LeafCluster cluster = clusters.get(clusterIndex);

        // Show info FIRST
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Leaf/Cluster Information");
        alert.setHeaderText("Cluster #" + cluster.number);
        alert.setContentText(
                "Size: " + cluster.size + " pixels\n" +
                        "Position: (" + cluster.minX + "," + cluster.minY + ") to (" +
                        cluster.maxX + "," + cluster.maxY + ")\n" +
                        "Width: " + cluster.getWidth() + " px, Height: " + cluster.getHeight() + " px"
        );
        alert.showAndWait();

        // Then highlight
        highlightSingleCluster(cluster);

        statusLabel.setText("Cluster #" + cluster.number + " - " + cluster.size + " pixels");
    }

    @FXML
    private void handleClear() {
        if (originalOverlayCanvas != null) {
            originalOverlayCanvas.getGraphicsContext2D().clearRect(0, 0, 512, 512);
        }
        if (bwOverlayCanvas != null) {
            bwOverlayCanvas.getGraphicsContext2D().clearRect(0, 0, 512, 512);
        }
        if (bwImage != null && bwImageView != null) {
            bwImageView.setImage(bwImage);
        }
        if (tspAnimation != null) {
            tspAnimation.stop();
        }
        tspPath = null;
        statusLabel.setText("Cleared");
    }

    @FXML
    private void handleReset() {
        if (originalImage != null) {
            filteredImage = null;
            originalImageView.setImage(originalImage);

            hueMinSlider.setValue(0);
            hueMaxSlider.setValue(60);
            saturationSlider.setValue(0.2);
            brightnessSlider.setValue(0.15);

            clusters = null;
            leafCountLabel.setText("Leaves: 0");
            bwImage = null;
            clusterColors.clear();
            if (tspAnimation != null) tspAnimation.stop();
            tspPath = null;
            statusLabel.setText("Reset");
        }
    }

    @FXML
    private void handleImageDetails() {
        if (originalImage == null) {
            showAlert("Details", "No image loaded");
            return;
        }

        String details = "Image Size: " + imageWidth + " x " + imageHeight + "\n";
        details += "Leaf Clusters: " + (clusters != null ? clusters.size() : 0);

        if (clusters != null && !clusters.isEmpty()) {
            int totalPixels = clusters.stream().mapToInt(c -> c.size).sum();
            details += "\nTotal leaf pixels: " + totalPixels;
            details += "\nLargest cluster: #" + clusters.get(0).number + " (" + clusters.get(0).size + " pixels)";
            details += "\nSmallest cluster: #" + clusters.get(clusters.size() - 1).number + " (" + clusters.get(clusters.size() - 1).size + " pixels)";
        }
        showAlert("Image Details", details);
    }

    @FXML
    private void handleNoiseSettings() {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(minLeafSize));
        dialog.setTitle("Noise Reduction");
        dialog.setHeaderText("Minimum leaf cluster size");
        dialog.setContentText("Clusters smaller than this will be ignored (pixels):\nCurrent: " + minLeafSize);

        dialog.showAndWait().ifPresent(result -> {
            try {
                int newSize = Integer.parseInt(result);
                minLeafSize = newSize;
                statusLabel.setText("Noise filter set to: " + minLeafSize + " pixels");
                if (bwImage != null) {
                    handleFindLeaves();
                }
            } catch (NumberFormatException e) {
                showAlert("Error", "Please enter a valid number");
            }
        });
    }

    @FXML
    private void handleExit() {
        System.exit(0);
    }

    private void showAlert(String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}