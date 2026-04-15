📱 Mobile QR Code Threat Detection using VirusTotal & Machine Learning

This project presents a mobile-based security solution designed to detect malicious QR codes (quishing attacks) by integrating VirusTotal API and Machine Learning techniques. The system aims to protect users from phishing and malware threats embedded within QR codes commonly found in emails, advertisements, and public spaces.

🚀 Project Overview

QR codes are widely used for quick access to websites and services. However, attackers increasingly exploit them to redirect users to malicious URLs. This project provides a proactive detection mechanism by scanning QR codes and analyzing their embedded links for potential threats.

The application combines:

Real-time QR code scanning
URL reputation analysis via VirusTotal
Machine learning-based classification for enhanced threat detection
🔍 Key Features

📷 QR Code Scanner
Scan QR codes using the mobile device camera or import images from the gallery.
🌐 URL Extraction
Automatically extracts URLs embedded within scanned QR codes.
🛡️ VirusTotal Integration
Checks URLs against multiple antivirus engines using the VirusTotal API.
🤖 Machine Learning Detection
Uses a trained ML model to classify URLs as safe or malicious based on features such as:
URL length
Domain characteristics
Presence of suspicious patterns
⚡ Real-Time Analysis
Provides instant feedback to users on whether a QR code is safe to access.
📊 Result Visualization
Displays analysis results clearly, including risk level and detection details.
🧠 Technologies Used
Android (Java/Kotlin)
ML Kit / ZXing (QR code scanning)
Python (for ML model training)
VirusTotal API
Kibana (for monitoring and visualization, if integrated)
🏗️ System Architecture
User scans a QR code.
The system extracts the embedded URL.
The URL is sent to:
VirusTotal API for reputation analysis
Machine Learning model for prediction
Results are combined and displayed to the user.
🎯 Objectives
Detect and prevent QR code phishing (quishing) attacks
Enhance mobile security awareness
Provide a lightweight and efficient threat detection tool
📌 Future Improvements
Offline ML model optimization
Integration with email filtering systems
Advanced threat intelligence integration
UI/UX enhancements
⚠️ Disclaimer

This project is developed for educational and research purposes. While it improves QR code security, it should not be considered a complete replacement for enterprise-level security solutions.
