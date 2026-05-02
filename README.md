# 📱 Mobile QR Code Threat Detection using VirusTotal & Machine Learning

## 🚀 Project Overview

This project presents a **mobile security application** designed to detect malicious QR codes (commonly known as *quishing attacks*) by combining **threat intelligence** from VirusTotal with **machine learning-based URL classification**.

As QR codes become widely adopted in payments, marketing, and public services, attackers increasingly exploit them to redirect users to phishing websites or malware. This application provides a **proactive defense mechanism** by scanning QR codes and analyzing their embedded URLs in real time before users interact with them.

---

## 🔍 Key Features

### 📷 QR Code Scanning

* Scan QR codes using the device camera
* Import QR images from gallery

### 🌐 URL Extraction

* Automatically extracts embedded URLs from scanned QR codes
* Supports detection of hidden or obfuscated links

### 🛡️ Threat Intelligence Integration

* Queries URLs against multiple security engines via VirusTotal
* Identifies known malicious or suspicious domains

### 🤖 Machine Learning Detection

* Enhances detection using a trained ML model
* Classifies URLs as **safe or malicious** based on features such as:

  * URL length and structure
  * Domain entropy and characteristics
  * Suspicious keywords and patterns

### ⚡ Real-Time Analysis

* Combines ML predictions and threat intelligence
* Provides **instant risk assessment** before user interaction

### 📊 Result Visualization

* Displays clear results including:

  * Risk level (Safe / Suspicious / Malicious)
  * Detection sources
  * Supporting analysis details

---

## 🧠 Technologies Used

* **Android (Java/Kotlin)** – Mobile application development
* **ML Kit / ZXing** – QR code scanning
* **Python** – Machine learning model training
* **VirusTotal API** – Threat intelligence integration

---

## 🏗️ System Architecture

1. User scans a QR code via the mobile application
2. The system extracts the embedded URL
3. The URL is analyzed through:

   * Threat intelligence (VirusTotal API)
   * Machine learning classification model
4. Results are aggregated and presented to the user in real time

---

## 🎯 Objectives

* Detect and prevent QR code-based phishing (quishing) attacks
* Improve user awareness of mobile security threats
* Provide a lightweight, fast, and accessible security tool

---

## 📈 Key Value

* Adds a **layer of protection** before users access potentially harmful links
* Reduces reliance on a single detection method by combining **AI + threat intelligence**
* Demonstrates practical application of **mobile security + machine learning + API integration**

---

## 🔮 Future Improvements

* Optimize ML model for **offline detection**
* Integrate with **email and messaging platforms**
* Expand threat intelligence sources beyond VirusTotal
* Improve UI/UX for better user experience
* Add **real-time alerting and logging system**

---

## ⚠️ Disclaimer

This project is developed for **educational and research purposes only**. While it enhances QR code security, it is not intended to replace enterprise-grade security solutions.
