import os
import math
import joblib
import whois
import dns.resolver
import requests
import datetime
from bs4 import BeautifulSoup
from urllib.parse import urlparse
from flask import Flask, request, jsonify
import pandas as pd

# ================================
# --- Load models ---
# ================================
rf_light = joblib.load("rf_model_light.pkl")
rf_full = joblib.load("rf_model_full.pkl")

light_classes = rf_light.classes_
full_classes = rf_full.classes_

# ================================
# --- Feature lists (MUST match training) ---
# ================================
light_features = [
    "url_length", "hostname_length", "ip", "https_token",
    "ratio_digits_url", "ratio_digits_host",
    "prefix_suffix", "shortening_service", "random_domain",
    "dns_record", "domain_age"
]

full_features = light_features + [
    "domain_registration_length", "web_traffic",
    "google_index", "page_rank",
    "nb_hyperlinks", "ratio_intHyperlinks", "ratio_extHyperlinks",
    "login_form", "iframe", "popup_window", "safe_anchor",
    "submit_email", "onmouseover"
]


# ================================
# --- Helper functions ---
# ================================
def shannon_entropy(s):
    if not s:
        return 0
    probabilities = [float(s.count(c)) / len(s) for c in set(s)]
    return -sum(p * math.log2(p) for p in probabilities if p > 0)

def is_random_domain(domain, threshold=4.0):
    try:
        name = domain.split(".")[0]
        entropy = shannon_entropy(name.lower())
        return 1 if entropy > threshold else 0
    except:
        return 0

def get_page_rank(domain):
    try:
        api_key = os.environ.get("OPR_API_KEY", "k08ccswsocoow0ggw8g4sssccoocgs80k0s8sos4")
        if not api_key:
            return 0
        url = f"https://openpagerank.com/api/v1.0/getPageRank?domains[]={domain}"
        headers = {"API-OPR": api_key}
        r = requests.get(url, headers=headers, timeout=5).json()
        if "response" in r and len(r["response"]) > 0:
            rank = r["response"][0].get("rank", 0) or 0
            return int(rank)
        return 0
    except:
        return 0

# ================================
# --- Feature extraction ---
# ================================
def extract_features(url):
    if not url.startswith(("http://", "https://")):
        url = "http://" + url
    parsed = urlparse(url)
    domain = parsed.netloc.lower()
    features = {}

    # --- Lexical features ---
    features.update({
        "url_length": len(url),
        "hostname_length": len(parsed.netloc),
        "ip": 1 if parsed.netloc.replace(".", "").isdigit() else 0,
        "https_token": 1 if parsed.scheme == "https" else 0,
        "ratio_digits_url": sum(c.isdigit() for c in url) / len(url) if len(url) > 0 else 0,
        "ratio_digits_host": sum(c.isdigit() for c in parsed.netloc) / len(parsed.netloc) if len(parsed.netloc) > 0 else 0,
        "prefix_suffix": 1 if "-" in parsed.netloc else 0,
        "shortening_service": 1 if any(s in url for s in ["bit.ly", "tinyurl"]) else 0,
        "random_domain": is_random_domain(parsed.netloc)
    })

    # --- WHOIS & DNS ---
    try:
        w = whois.whois(domain)
        creation_date = None
        expiration_date = None

        if isinstance(w.creation_date, list) and w.creation_date:
            creation_date = w.creation_date[0]
        else:
            creation_date = w.creation_date

        if isinstance(w.expiration_date, list) and w.expiration_date:
            expiration_date = w.expiration_date[0]
        else:
            expiration_date = w.expiration_date

        if creation_date:
            features["domain_age"] = (datetime.datetime.now() - creation_date).days // 30
        else:
            features["domain_age"] = 0

        if creation_date and expiration_date:
            features["domain_registration_length"] = (expiration_date - creation_date).days // 30
        else:
            features["domain_registration_length"] = 0
    except:
        features["domain_age"] = 0
        features["domain_registration_length"] = 0

    try:
        dns.resolver.resolve(domain, 'A')
        features["dns_record"] = 1
    except:
        features["dns_record"] = 0

    try:
        q = f"https://www.google.com/search?q=site:{domain}"
        r = requests.get(q, headers={"User-Agent": "Mozilla/5.0"}, timeout=5)
        features["google_index"] = 0 if "did not match any documents" in r.text else 1
    except:
        features["google_index"] = 0

    features["web_traffic"] = features["google_index"]
    features["page_rank"] = min(get_page_rank(domain), 10)

    # --- HTML features ---
    html_feats = {
        "nb_hyperlinks": 0,
        "ratio_intHyperlinks": 0,
        "ratio_extHyperlinks": 0,
        "login_form": 0,
        "iframe": 0,
        "popup_window": 0,
        "safe_anchor": 1,
        "submit_email": 0,
        "onmouseover": 0
    }
    try:
        r = requests.get(url, headers={"User-Agent": "Mozilla/5.0"}, timeout=5)
        if r.status_code == 200:
            soup = BeautifulSoup(r.text, "html.parser")
            links = soup.find_all("a")
            html_feats["nb_hyperlinks"] = len(links)
            int_links, ext_links, empty_links = 0, 0, 0
            for link in links:
                href = link.get("href")
                if not href:
                    continue
                if href.startswith("#") or href.strip() == "":
                    empty_links += 1
                elif domain in href or href.startswith("/"):
                    int_links += 1
                else:
                    ext_links += 1
            total_links = int_links + ext_links
            if total_links > 0:
                html_feats["ratio_intHyperlinks"] = int_links / total_links
                html_feats["ratio_extHyperlinks"] = ext_links / total_links
                html_feats["safe_anchor"] = 1 - (empty_links / total_links)
            if soup.find("input", {"type": "password"}):
                html_feats["login_form"] = 1
            if soup.find("iframe"):
                html_feats["iframe"] = 1
            if "window.open(" in r.text:
                html_feats["popup_window"] = 1
            if "mailto:" in r.text:
                html_feats["submit_email"] = 1
            if "onmouseover" in r.text:
                html_feats["onmouseover"] = 1
    except:
        pass

    features.update(html_feats)

    # Ensure all features exist
    for f in full_features:
        if f not in features:
            features[f] = 0

    return features

# ================================
# --- Correction rules ---
# ================================
def apply_correction(feats, raw_label, phishing_prob, legit_prob):
    if raw_label == "phishing":
        strong_legit_signals = 0
        if feats.get("dns_record", 0) == 1: strong_legit_signals += 1
        if feats.get("google_index", 0) == 1: strong_legit_signals += 1
        if feats.get("page_rank", 0) >= 5: strong_legit_signals += 1
        if feats.get("domain_age", 0) > 24: strong_legit_signals += 1
        if feats.get("domain_registration_length", 0) > 12: strong_legit_signals += 1

        # override only if phishing prob is weak/moderate
        if strong_legit_signals >= 3 and phishing_prob < 0.9:
            return "legitimate", phishing_prob * 0.3, min(1.0, legit_prob + 0.5)

    return raw_label, phishing_prob, legit_prob


# ================================
# --- Hybrid Prediction ---
# ================================
def hybrid_predict(feats, threshold=0.8):
    # Light model
    X_light = pd.DataFrame([[feats.get(f, 0) for f in light_features]], columns=light_features)
    proba_light = rf_light.predict_proba(X_light)[0]

    phishing_prob_light = proba_light[list(light_classes).index(1)]
    legit_prob_light = proba_light[list(light_classes).index(0)]
    raw_label_light = "phishing" if phishing_prob_light > legit_prob_light else "legitimate"
    confidence_light = max(phishing_prob_light, legit_prob_light)

    if confidence_light < threshold:
        X_full = pd.DataFrame([[feats.get(f, 0) for f in full_features]], columns=full_features)
        proba_full = rf_full.predict_proba(X_full)[0]

        phishing_prob = proba_full[list(full_classes).index(1)]
        legit_prob = proba_full[list(full_classes).index(0)]
        raw_label = "phishing" if phishing_prob > legit_prob else "legitimate"
        model_used = "full"
    else:
        phishing_prob, legit_prob = phishing_prob_light, legit_prob_light
        raw_label = raw_label_light
        model_used = "light"

    return raw_label, phishing_prob, legit_prob, model_used

# ================================
# --- Flask App ---
# ================================
app = Flask(__name__)

@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "light_features": len(light_features),
        "full_features": len(full_features)
    })

@app.route("/predict", methods=["POST"])
def predict():
    data = request.get_json()
    url = data.get("url")

    if not url:
        return jsonify({"error": "Missing 'url' field"}), 400

    feats = extract_features(url)
    raw_label, phishing_prob, legit_prob, model_used = hybrid_predict(feats)

    final_label, phishing_prob, legit_prob = apply_correction(
        feats, raw_label, phishing_prob, legit_prob
    )

    return jsonify({
        "url": url,
        "model_used": model_used,
        "raw_prediction": raw_label,
        "final_prediction": final_label,
        "phishing_prob": round(phishing_prob, 3),
        "legitimate_prob": round(legit_prob, 3),
        "features_count": len(feats),
        "features": feats
    })

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
