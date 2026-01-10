# Method-Level Bugginess Prediction and Refactoring Analysis

This repository contains the final project developed for the course **Ingegneria del Software 2** at the University of Rome Tor Vergata (MSc in Computer Engineering).

## Analyzed Projects

The empirical analysis was conducted on the following Apache open-source systems:
* **Apache BookKeeper**
* **Apache Syncope**

## Project Goals

The project aims to:

* build historical **method-level defect prediction datasets** by integrating Jira and Git data;
* compare different machine learning classifiers using **Weka**, under both cross-validation and temporal validation; 
* identify actionable code metrics correlated with method bugginess; 
* perform targeted **refactoring** guided by most correlated metrics; 
* estimate, through a **What-If analysis**, how many defective methods could have been avoided by removing code smells.

## Results

The repository includes all the artifacts produced during the experimental evaluation, organized as follows:

### Datasets

The datasets used for defect prediction are available in the `csvFiles/` directory:

* `csvFiles/bookkeeper/Dataset.csv` – method-level historical dataset for Apache BookKeeper
* `csvFiles/syncope/Dataset.csv` – method-level historical dataset for Apache Syncope

### Classification Results

The results of the defect prediction experiments are reported as boxplots in the `plot/` directory, separated by project and validation strategy.

### Refactoring Analysis

The artifacts related to the refactoring activity are stored in the `refactoringReport/` directory:

* CSV files reporting the comparison of code metrics before and after refactoring
* Java files containing the original and refactored versions of the selected methods

### What-If Analysis

The results of the What-If analysis are available in the `whatIf/` directory, organized by project:

* `B_plus.csv, B.csv, C.csv` – datasets used to simulate the removal of code smells
* `whatIf_results_*.csv` – summary of the estimated reduction in buggy methods