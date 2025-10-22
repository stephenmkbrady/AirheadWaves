#!/bin/sh
set -e

# Create release JSON
cat > release.json <<EOF
{
  "name": "Release ${VERSION_NAME}",
  "tag_name": "${CI_COMMIT_TAG}",
  "description": "Download APK below",
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
