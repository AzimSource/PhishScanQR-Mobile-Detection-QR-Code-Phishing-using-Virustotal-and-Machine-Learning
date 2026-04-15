import sys
import urllib.parse
from app import extract_features, hybrid_predict, apply_correction

def test_url(url):
    print(f"\n🔍 Testing URL: {url}\n")

    # Normalize / encode URL so Windows CMD doesn't break on '&'
    safe_url = urllib.parse.quote(url, safe=":/?=&.")  

    # Extract features
    feats = extract_features(safe_url)

    # Run hybrid prediction
    raw_label, phishing_prob, legit_prob, model_used = hybrid_predict(feats)

    # Apply correction
    final_label, phishing_prob, legit_prob = apply_correction(
        feats, raw_label, phishing_prob, legit_prob
    )

    # Print results
    print(f"Model used: {model_used}")
    print(f"Raw prediction: {raw_label}")
    print(f"Final prediction: {final_label}")
    print(f"Probabilities → phishing: {phishing_prob:.3f}, legitimate: {legit_prob:.3f}")
    print(f"Extracted {len(feats)} features.")

    return final_label

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("⚠️ Usage: python test.py <url>")
        sys.exit(1)

    url = sys.argv[1]
    test_url(url)
