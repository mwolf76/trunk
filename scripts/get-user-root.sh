#!/bin/sh
# Example script to demonstrate doing a REST request behind oauth2
# $ scripts/get-user-root.sh admin admin

# params
CLIENT_ID="trunk"
CLIENT_SECRET="9a716081-622e-48e9-9930-58beb765f4e6"
USERNAME="$1"
PASSWORD="$2"

# Retrieve access token using password flow
TOKEN=`2>/dev/null curl \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "grant_type=password" \
  "http://localhost:9000/auth/realms/master/protocol/openid-connect/token" | jq -r '.access_token'`

# Perform the request
2>/dev/null curl -L \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/json" \
  http://localhost:8080/protected/root | jq
