#!/bin/sh
set -e

# Create stable ref-based APK URL
STABLE_APK_URL="${CI_PROJECT_URL}/-/jobs/artifacts/${CI_COMMIT_TAG}/raw/app/build/outputs/apk/release/${APK_NAME}?job=build_release"

# Create release JSON
cat > release.json <<EOF
{
  "name": "Release ${VERSION_NAME}",
  "tag_name": "${CI_COMMIT_TAG}",
  "description": "## Download\n\n[${APK_NAME}](${STABLE_APK_URL})\n\n**SHA256:** (to be added)\n\n---\n\nFor F-Droid submission.",
  "assets": {
    "links": [{
      "name": "${APK_NAME}",
      "url": "${APK_URL}",
      "link_type": "package"
    }]
  }
}
EOF

cat release.json

# Create release
curl --fail --request POST \
  --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
  --header "Content-Type: application/json" \
  --data @release.json \
  "${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/releases"
