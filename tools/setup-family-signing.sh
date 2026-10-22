#!/usr/bin/env bash
# One-time setup for stable Family Edition APK signing. Never upload this directory to GitHub.
set -euo pipefail
umask 077

command -v openssl >/dev/null || { echo 'OpenSSL is required.' >&2; exit 1; }

SIGNING_DIR="${FAMILY_EDITION_SIGNING_DIR:-$HOME/.config/cleanyourtv/signing}"
KEYSTORE="$SIGNING_DIR/family-edition-release.p12"
PASSWORD="$SIGNING_DIR/keystore-password.txt"
BASE64="$SIGNING_DIR/keystore-base64.txt"

mkdir -p "$SIGNING_DIR"
chmod 700 "$SIGNING_DIR"
if [[ -e "$KEYSTORE" || -e "$PASSWORD" || -e "$BASE64" ]]; then
  if [[ ! -s "$KEYSTORE" || ! -s "$PASSWORD" || ! -s "$BASE64" ]]; then
    echo "Incomplete signing files in $SIGNING_DIR; no key will be overwritten." >&2
    exit 1
  fi
  echo "Signing material already exists in $SIGNING_DIR. Not rotating the signing identity."
  echo 'Back it up securely; reuse these exact files for future builds.'
  exit 0
fi

TMP=$(mktemp -d "$SIGNING_DIR/.generating.XXXXXXXX")
trap 'rm -rf "$TMP"' EXIT
openssl rand -hex 32 | tr -d '\n' > "$TMP/keystore-password.txt"
openssl req -x509 -newkey rsa:3072 -sha256 -days 36500 -nodes -subj '/CN=Family Edition Launcher' -keyout "$TMP/private.pem" -out "$TMP/certificate.pem" >/dev/null 2>&1
openssl pkcs12 -export -inkey "$TMP/private.pem" -in "$TMP/certificate.pem" -name family-edition -out "$TMP/family-edition-release.p12" -passout "file:$TMP/keystore-password.txt"
openssl pkcs12 -in "$TMP/family-edition-release.p12" -info -noout -passin "file:$TMP/keystore-password.txt" >/dev/null 2>&1
base64 -w 0 "$TMP/family-edition-release.p12" > "$TMP/keystore-base64.txt"
printf '\n' >> "$TMP/keystore-base64.txt"

mv "$TMP/family-edition-release.p12" "$KEYSTORE"
mv "$TMP/keystore-password.txt" "$PASSWORD"
mv "$TMP/keystore-base64.txt" "$BASE64"
chmod 600 "$KEYSTORE" "$PASSWORD" "$BASE64"

echo 'Stable Family Edition signing key generated locally.'
echo "Private directory: $SIGNING_DIR"
echo 'Create these two GitHub Actions repository secrets:'
echo "  FAMILY_EDITION_KEYSTORE_BASE64     <- $BASE64"
echo "  FAMILY_EDITION_KEYSTORE_PASSWORD   <- $PASSWORD"
echo 'Keep both values private. Do NOT paste them into ChatGPT or commit them to GitHub.'
echo 'Back up the .p12 and password in secure storage; losing either breaks future update signing.'
