#!/bin/sh
set -e

# Create release JSON (without assets, we'll add them separately via API)
cat > release.json <<EOF
{
  "name": "Release ${VERSION_NAME}",
  "tag_name": "${CI_COMMIT_TAG}",
  "description": "Release ${VERSION_NAME}"
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
# Use ref-based URL so it remains stable across rebuilds
REF_BASED_APK_URL="${CI_PROJECT_URL}/-/jobs/artifacts/${CI_COMMIT_TAG}/raw/app/build/outputs/apk/release/${APK_NAME}?job=build_release"

echo "Creating permanent release asset link"

curl --fail --request POST \
  --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
  --data "name=${APK_NAME}" \
  --data "url=${REF_BASED_APK_URL}" \
  --data "filepath=/${APK_NAME}" \
  --data "link_type=package" \
  "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases/${CI_COMMIT_TAG}/assets/links"
