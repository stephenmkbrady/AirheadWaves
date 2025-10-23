#!/bin/sh
set -e

# Create stable ref-based APK URL for F-Droid
STABLE_APK_URL="${CI_PROJECT_URL}/-/releases/${CI_COMMIT_TAG}/downloads/${APK_NAME}"

# Create release JSON (without assets, we'll add them separately)
cat > release.json <<EOF
{
  "name": "Release ${VERSION_NAME}",
  "tag_name": "${CI_COMMIT_TAG}",
  "description": "## Download\n\n[${APK_NAME}](${STABLE_APK_URL})"
}
EOF

cat release.json

# Create release
curl --fail --request POST \
  --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
  --header "Content-Type: application/json" \
  --data @release.json \
  "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases"

# Create permanent release asset link (stable URL for F-Droid)
cat > asset_link.json <<EOF
{
  "name": "${APK_NAME}",
  "url": "${APK_URL}",
  "link_type": "package"
}
EOF

echo "Creating permanent release asset link"
cat asset_link.json

curl --fail --request POST \
  --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
  --header "Content-Type: application/json" \
  --data @asset_link.json \
  "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases/${CI_COMMIT_TAG}/assets/links"
