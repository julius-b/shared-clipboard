# Shared Clipboard / Access Anywhere

Was supposed to be a shared clipboard app, but accessing the clipboard on android doesn't really work anymore :(

Now it's an app to access media files from different devices (desktop, android).

Status: early development

## Overview
- [Kotlin Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- [server/](server/src/main/kotlin/app/mindspaces/clipboard): code for HTTP server
  - run: `./gradlew :server:run`
- [composeApp/](composeApp/src): code for clients (desktop & android)
  - run desktop: `./gradlew :composeApp:run`
  - run android: `./gradlew assembleDebug installDebug`
- [shared/](shared): shared api definitions, etc.

## Components

### Client
- DI: [kotlin-inject](https://github.com/evant/kotlin-inject) & [kotlin-inject-anvil](https://github.com/amzn/kotlin-inject-anvil)
- Background work: [Android Work Manager](https://developer.android.com/topic/libraries/architecture/workmanager/)
  - [androidMain/SyncWorker.kt](composeApp/src/androidMain/kotlin/app/mindspaces/clipboard/work/SyncWorker.kt)
- Image loading: [Coil3](https://github.com/coil-kt/coil)
- Navigation: [Circuit](https://github.com/slackhq/circuit/)
- Database: [SQLDelight](https://github.com/sqldelight/sqldelight) (generates typesafe Kotlin APIs from SQL)
  - migrations: [db/](composeApp/src/commonMain/sqldelight/app/mindspaces/clipboard/db)
- Networking: [Ktor](https://ktor.io)
- Serialization: [kotlinx-serialization](https://github.com/Kotlin/kotlinx.serialization)

### Server
- Database: [Exposed](https://github.com/JetBrains/Exposed) (ORM framework)
  - `h2` for development, `postgres` in production
- Networking: [Ktor](https://ktor.io)
- Serialization: [kotlinx-serialization](https://github.com/Kotlin/kotlinx.serialization)

## Bash Client

### Dev
```shell
export host=http://localhost:8080
export ws_host=ws://localhost:8080/ws
```

### Installations Api
#### List
```shell
curl "$host/api/v1/installations"
```

#### Create
```shell
output=$(curl -s -d '{"id":"'$(uuidgen)'","name":"Curl client","desc":"README","os":"desktop","client":"opia_readme/1"}' -H "Content-Type: application/json" -X PUT "$host/api/v1/installations")
echo "$output"
export installation_id=$(jq -r '.data.id' <<< "$output")
echo "installation_id: $installation_id"
```

### Account Properties Api
#### Create
```shell
# 044 668 18 00 (https://github.com/google/libphonenumber)
export phone_no="+41446681800"
output=$(curl -s -d '{"content":"'$phone_no'"}' -H "Installation-Id: $installation_id" -H "Content-Type: application/json" "$host/api/v1/accounts/properties")
echo "$output"
export account_phone_no_id=$(jq -r '.data.id' <<< "$output")
echo "account_phone_no_id: $account_phone_no_id"
export phone_verification_code=$(jq -r '.data.verification_code' <<< "$output")
echo "phone_verification_code: $phone_verification_code"
```

### Accounts Api
#### List (Authentication required)
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/accounts"
```

#### Create
```shell
output=$(curl -s -d '{"name":"User Name","secret":"secret12"}' -H "Challenge-Response: $account_phone_no_id=$phone_verification_code" -H "Installation-Id: $installation_id" -H "Content-Type: application/json" "$host/api/v1/accounts")
echo "$output"
export account_id=$(jq -r '.data.id' <<< "$output")
echo "account_id: $account_id"
export sess_id=$(jq -r '.hints.session.id' <<< "$output")
echo "sess_id: $sess_id"
export access_token=$(jq -r '.hints.session.access_token' <<< "$output")
echo "access_token: $access_token"
export refresh_token=$(jq -r '.hints.session.refresh_token' <<< "$output")
echo "refresh_token: $refresh_token"
```

#### Signup
```shell
output=$(curl -s -d '{"name":"Signup Name","secret":"secret12","email":"test@hotbling.wtf"}' -H "Installation-Id: $installation_id" -H "Content-Type: application/json" "$host/api/v1/accounts/signup")
echo "$output"
export account_id=$(jq -r '.data.id' <<< "$output")
echo "account_id: $account_id"
export sess_id=$(jq -r '.hints.session.id' <<< "$output")
echo "sess_id: $sess_id"
export access_token=$(jq -r '.hints.session.access_token' <<< "$output")
echo "access_token: $access_token"
export refresh_token=$(jq -r '.hints.session.refresh_token' <<< "$output")
echo "refresh_token: $refresh_token"
```

### Installation Links Api
#### List own (Authentication required)
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/accounts/$account_id/installations"
```

### Auth Sessions Api
#### List (Authentication required)
```shell
curl "$host/api/v1/auth_sessions"
```

#### Login with phone-no
- requires `Installation`
- redo when receiving "invalid or expired token", though apps use refresh route
```shell
output=$(curl -s -d '{"unique":"'$phone_no'","secret":"secret12","cap_chat":true}' -H "Installation-Id: $installation_id" -H "Content-Type: application/json" "$host/api/v1/auth_sessions")
echo "$output"
export sess_id=$(jq -r '.data.id' <<< "$output")
echo "sess_id: $sess_id"
export access_token=$(jq -r '.data.access_token' <<< "$output")
echo "access_token: $access_token"
export refresh_token=$(jq -r '.data.refresh_token' <<< "$output")
echo "refresh_token: $refresh_token"
```

#### Refresh
```shell
output=$(curl -s -X POST -H "Installation-Id: $installation_id" -H "Refresh-Token: Bearer $refresh_token" -H "Content-Type: application/json" "$host/api/v1/auth_sessions/refresh")
echo "$output"
export sess_id=$(jq -r '.data.id' <<< "$output")
echo "sess_id: $sess_id"
export access_token=$(jq -r '.data.access_token' <<< "$output")
echo "access_token: $access_token"
export refresh_token=$(jq -r '.data.refresh_token' <<< "$output")
echo "refresh_token: $refresh_token"
```

### Medias Api
#### List own (Authentication required)
- **server returns medias for which this installation-link-id has submitted no receipt, or where the receipt mismatches the server-side media's `has_file` or `has_thumb`**
```shell
output=$(curl -s -H "Authorization: Bearer $access_token" "$host/api/v1/medias")
echo "$output"
export media_id=$(jq -r '.data[0].id' <<< "$output")
echo "media_id (latest unreceived): $media_id"
```

#### List all (Root Authentication required)
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/medias?all"
```

#### Create (via thumbnail)
```shell
# download an image (6.4M), src: https://images.nasa.gov/details/GSFC_20171208_Archive_e002152
file_path="testdata/image.jpg"
(ls "$file_path" && echo "exists: $file_path") || (echo "downloading..." && wget -O "$file_path" "https://images-assets.nasa.gov/image/GSFC_20171208_Archive_e002152/GSFC_20171208_Archive_e002152~orig.jpg")

export media_id=$(uuidgen)
size=$(wc -c < "$file_path")
curl -X POST -F "path=/storage/emulated/0/Pictures/Carina Nebula.jpg" -F "file=@$file_path"  -F "size=$size" -F "dir=/storage/emulated/0/Pictures" -F "cre=$(date +%s%3N)" -F "mod=$(date +%s%3N)" -H "Installation-Id: $installation_id" "$host/api/v1/medias/$media_id/thumb"
```

#### Create (via file)
```shell
# download a video (125.2M), src: https://archive.org/details/stargate-collection-1994-2018
file_path="testdata/video.mp4"
(ls "$file_path" && echo "exists: $file_path") || (echo "downloading..." && wget -O "$file_path" "https://archive.org/download/stargate-collection-1994-2018/Stargate%20Collection%20%281994-2018%29/Stargate%20SG1%20S01-S10%20%281997-%29%20%2B%202%20Movies/SG-1%20S04%20%28360p%20re-blurip%29/SG-1%20S04E06%20Window%20of%20Opportunity.mp4")

export media_id=$(uuidgen)
size=$(wc -c < "$file_path")
curl -X POST -F "path=/storage/emulated/0/Movies/SG-1 S04E06 Window of Opportunity.mp4" -F "file=@$file_path" -F "size=$size" -F "dir=/storage/emulated/0/Videos" -F "media-type=Video" -F "cre=$(date +%s%3N)" -F "mod=$(date +%s%3N)" -H "Installation-Id: $installation_id" "$host/api/v1/medias/$media_id/file"
```

#### Download Thumbnail
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/medias/$media_id/thumb/raw" -o /tmp/thumb.jpg
ls -lah /tmp/thumb.jpg # size info
xdg-open /tmp/thumb.jpg
```

#### Download File
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/medias/$media_id/file/raw" -o /tmp/file
ls -lah /tmp/file # size info
xdg-open /tmp/file
vlc /tmp/file
```

### Media Receipts Api
#### List all (Root Authentication required)
```shell
curl -H "Authorization: Bearer $access_token" "$host/api/v1/media_receipts"
```

#### Create
##### Create Thumbnail receipt
```shell
curl -d '{"has_thumb":"true"}' -H "Authorization: Bearer $access_token" -H "Content-Type: application/json" "$host/api/v1/medias/$media_id/receipts"
```

##### Create File and Thumbnail receipt
- `has_file`-only is also possible and valid
```shell
curl -d '{"has_file":"true"}' -H "Authorization: Bearer $access_token" -H "Content-Type: application/json" "$host/api/v1/medias/$media_id/receipts"
```

## TODO
- access clipboard on selected devices using root (not available on GrapheneOS), accessibility service, device admin (both won't be allowed in play store)
- thymeleaf & htmx web client vs wasmJS web client
- end-to-end encryption using Noise
- password hashing (preferably argon2id)
- tests for stable components

## Drawables:
- src: https://fonts.google.com/icons, converted to xml using Resource Manager 'from local file'
