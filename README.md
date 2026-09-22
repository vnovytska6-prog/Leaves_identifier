# Autumn Leaves Identification System

A JavaFX application for detecting and counting fallen autumn leaves in outdoor images.

## Demo
<img width="1100" height="555" alt="Autumn_Leaves" src="https://github.com/user-attachments/assets/ae9b4f42-ebbc-4f6f-a828-61d11cfd2eca" />

## Overview
This project was developed for a **Data Structures and Algorithms** assignment.  
The system processes an image of fallen leaves, converts it into a black-and-white representation using adjustable HSV thresholds, and uses a **Union-Find** algorithm to detect connected leaf clusters.

## Features
- Load and display an input image
- Convert the image to black and white
- Adjust hue, saturation, and brightness thresholds
- Detect leaf clusters using **Union-Find**
- Estimate and count detected leaves/clusters
- Visualize detected regions
- Colour connected components
- Run a simple **TSP-based path** across clusters

## Technologies
- Java
- JavaFX
- Union-Find / Disjoint Set
- Basic image processing
- TSP approximation
