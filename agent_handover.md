# Agent Handover — Chat App (Multi-Format E2EE Media Sharing)
_Last updated: 2026-03-11_

---

## 1. What Is Done ✅

### Core Architecture
- **End-to-End Encrypted messaging** over Firebase Realtime Database (RTDB) using ECDH key exchange + AES-256-GCM.
- **Ephemeral key pair** per session for forward secrecy. Each chat generates an EC key pair; sender attaches their ephemeral public key inside the encrypted envelope.
- **AndroidKeyStore** stores long-term identity keys (public + private) per user.
- **Inbox pop model**: RTDB is used only as a temporary delivery inbox. Messages are stored in the sender's RTDB node for the receiver to consume. After delivery, the `ChildEventListener` removes the RTDB entry via `snapshot.ref.removeValue()`.

### Cloudinary E2EE Media Upload
- All media (images, video, audio, PDF) is encrypted **locally** before upload.
- Encrypted blob is uploaded to Cloudinary using **unsigned uploads** with upload preset `ml_default`.
- Cloudinary URL + AES key + IV are written into the RTDB media envelope (all encrypted with the chatroom's shared secret).
- Cloudinary `resource_type = "auto"` is used so it accepts binary blobs regardless of extension.

### Categorized RTDB Inbox
```
/user_inboxes/{uid}/texts/{messageId}  → encrypted text envelope
/user_inboxes/{uid}/media/{messageId}  → encrypted media envelope
```
Both route through the same `createInboxListener()` function in `ChatRepository`.

### Data Models
- `MessageEntity` (Room) → version 4, has `mediaUrl`, `mediaKey`, `mediaIv`, `mediaType`, `mediaFileName`.
- `Message` (domain) → mirrors the entity with all nullable media fields.
- Room migration `MIGRATION_3_4` adds `mediaType TEXT` and `mediaFileName TEXT` to the `messages` table.

### Local Caching
- On **receive**: decrypted bytes are written to `context.filesDir/media/<mediaFileName>`.
- On **send**: the original unencrypted bytes are written to `context.filesDir/media/<mediaFileName>` before the Cloudinary upload starts, so the sender sees their own message immediately.
- `FileProvider` exposes `filesDir/media/` via `${packageName}.fileprovider` for `ACTION_VIEW` intents.

### External Storage Export
- Decrypted media is also written to:
  - `Downloads/ChatApp/Image` for images
  - `Downloads/ChatApp/Video` for videos
  - `Downloads/ChatApp/Audio` for audio
  - `Downloads/ChatApp/Document` for PDFs and other files
- Uses `MediaStore.Downloads.EXTERNAL_CONTENT_URI` (Scoped Storage compatible, API 29+).

### UI
- Attachment button opens an `ActivityResultContracts.OpenDocument` picker with MIME `*/*` (all file types).
- `DecryptedMedia` composable handles rendering:
  - **Image** → `Bitmap` decoded from cached bytes → shown as `Image()` composable.
  - **Video/Audio/Document** → Icon (PlayArrow / Audiotrack / Description) + filename shown.
  - Tapping any media fires an `ACTION_VIEW` intent via `FileProvider` URI.
- `ChatBubble` shows `[mediaType]` as content text for sent media (e.g. `[Image]`, `[Video]`).

### Security Guardrails (from prior session)
- Network connectivity check before channel establishment.
- `ErrorHandler` classifies exceptions into user-readable messages.
- Handles deleted accounts (missing public key) gracefully.
- All errors surface as a `Snackbar` in the UI via `_errorMessage` StateFlow.

---

## 2. What Is Currently Being Done 🔄

### Active Bug: "Corrupt Media" on Sender Side
**Symptom**: Immediately after sending a media file, the sender's own bubble shows **"Corrupt Media"** in red.

**Root diagnostic**:
- `DecryptedMedia` composable runs a `LaunchedEffect` for every message with `mediaUrl`.
- It checks if `filesDir/media/<mediaFileName>` exists and reads it directly (no decryption).
- The sender pre-caches the raw bytes to that file in `sendMedia()` before the Cloudinary upload.
- If the `mediaFileName` resolves the same way in the composable (via `message.mediaFileName`), it should load cleanly.
- The error is caught in a `catch(e: Exception)` block and sets `error = true` without printing the stack trace in older code. The **latest fix** added `Log.e("DecryptedMedia", ...)` to improve visibility.

**Most likely remaining causes** (not yet conclusively determined):
1. **Filename collision** — two different users share the same filename (e.g. `photo.jpg`). The cache file from one conversation is corrupted for another.
2. **FileProvider path mismatch** — `file_paths.xml` only exposes `files-path` with `path="."`. If the file is inside a `media/` subdirectory, it might not resolve. *(Check this!)*
3. **Race condition** — The composable recomposition triggers before `cachedFile.writeBytes(bytes)` completes.

**Fix in progress** (was cancelled by user):
- The plan was to add `isSentByMe` check in `DecryptedMedia` to skip the download path entirely and read directly from the file for sent messages. This would also prevent accidentally re-downloading a file the sender already has.

---

## 3. Known Errors Observed 🐛

| Error | File | Status |
|---|---|---|
| `'return' is prohibited here` at line 234 | `ChatRepository.kt` | ✅ Fixed — orphaned block from old `sendImageMessage` was deleted |
| `Unresolved reference 'getSharedSecret'` | `ChatRepository.kt` | ✅ Fixed — changed to use the class-level `sharedSecret` variable |
| `Argument type mismatch: actual type is Any, but ByteArray was expected` | `ChatRepository.kt` (encResult) | ✅ Fixed — changed `encResult.ciphertextBase64/ivBase64` to `encResult.ciphertext/iv` |
| `Unresolved reference 'Audiotrack', 'Description'` | `ChatScreen.kt` | ✅ Fixed — added `material-icons-extended:1.6.3` to `build.gradle.kts` |
| `Unresolved reference 'DocumentFile'` | `ChatScreen.kt` | ✅ Fixed — added `androidx.documentfile:documentfile:1.0.1` to `build.gradle.kts` |
| `Unresolved reference 'sendMediaMessage'` | `ChatScreen.kt` | ✅ Fixed — function was accidentally deleted; restored |
| **"Corrupt Media" on sender side** | `ChatScreen.kt` / `DecryptedMedia` | ❌ **Active — not yet resolved** |

---

## 4. Firebase Realtime Database (RTDB) Structure

```
users/
  {uid}/
    name: String
    status: String ("Available", "Deleted Account", etc.)
    publicKey: String (Base64-encoded EC public key)
    profilePhotoUrl: String?

user_inboxes/
  {uid}/
    texts/
      {messageId}/                ← EncryptedMessage object
        messageId: String
        senderId: String
        receiverId: String
        ciphertext: String        ← AES-256-GCM encrypted content (Base64)
        iv: String                ← AES IV (Base64)
        timestamp: Long
        ephemeralPublicKey: String? ← Sender's ephemeral EC public key

    media/
      {messageId}/                ← Same EncryptedMessage object
        messageId: String
        senderId: String
        receiverId: String
        ciphertext: String        ← Encrypted JSON payload (Base64):
                                     { mediaUrl, mediaKey, mediaIv,
                                       mediaType, mediaFileName }
        iv: String
        timestamp: Long
        ephemeralPublicKey: String?
```

### RTDB Security Rules (recommended — verify in Firebase Console)
```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "auth != null",
        ".write": "$uid === auth.uid"
      }
    },
    "user_inboxes": {
      "$uid": {
        ".read": "$uid === auth.uid",
        ".write": "auth != null"
      }
    }
  }
}
```

> **Important**: Inbox messages are deleted after delivery via `snapshot.ref.removeValue()`. If this fails (e.g. permission issues), messages pile up in RTDB.

---

## 5. Room Database (SQLite) Schema — `chat_database` Version 4

### `messages` table
| Column | Type | Notes |
|---|---|---|
| `messageId` | TEXT PK | UUID string |
| `senderId` | TEXT | Firebase Auth UID |
| `receiverId` | TEXT | Firebase Auth UID |
| `content` | TEXT | Plaintext or `[Image]`, `[Video]` etc. for media |
| `timestamp` | INTEGER | Epoch millis |
| `isSentByMe` | INTEGER | Boolean (0/1) |
| `isRead` | INTEGER | Boolean (0/1), default 0 |
| `mediaUrl` | TEXT NULL | Cloudinary `secure_url` |
| `mediaKey` | TEXT NULL | AES key (Base64) |
| `mediaIv` | TEXT NULL | AES IV (Base64) |
| `mediaType` | TEXT NULL | `"Image"`, `"Video"`, `"Audio"`, `"Document"` |
| `mediaFileName` | TEXT NULL | Original filename (e.g. `photo.jpg`) |

### `users` table
| Column | Type | Notes |
|---|---|---|
| `userId` | TEXT PK | Firebase Auth UID |
| `name` | TEXT | Display name |
| `profilePhotoUrl` | TEXT NULL | Cloudinary URL |
| `status` | TEXT | `"Available"`, `"Deleted Account"`, etc. |

**Migration history**: `1→2→3→4`. Migration 3→4 adds `mediaType` and `mediaFileName`.

---

## 6. Key Files and Their Purposes

| File | Purpose |
|---|---|
| `ChatRepository.kt` | Single source of truth for all messaging logic. Handles ECDH channel setup, `sendMessage`, `sendMediaMessage`, RTDB listeners, local DB persistence. |
| `ChatScreen.kt` | Contains `ChatViewModel`, `ChatScreen` composable, `DecryptedMedia` composable, and `ChatBubble`. This is the primary UI file for the chat view. |
| `KeyManager.kt` | Manages cryptographic keys: AndroidKeyStore identity keys, ephemeral ECDH key pairs, AES-256-GCM encrypt/decrypt. |
| `MediaCryptoHelper.kt` | Encrypts/decrypts raw `ByteArray` for media. Returns `{ ciphertext: ByteArray, mediaKeyBase64: String, mediaIvBase64: String }`. |
| `CloudinaryManager.kt` | Handles unsigned Cloudinary uploads. Cloud name: `drpzfssem`. Uses upload preset `ml_default`. |
| `MessageEntity.kt` | Room entity for messages (version 4 schema). |
| `Message.kt` | Domain model. Mirrors `MessageEntity`. Used throughout the UI layer. |
| `AppDatabase.kt` | Room database definition. Version 4. Contains `MIGRATION_3_4`. |
| `EncryptedMessage.kt` | Data class serialized to/from Firebase RTDB. Contains `ciphertext`, `iv`, `ephemeralPublicKey`. |
| `AndroidManifest.xml` | Declares `FileProvider` with authority `${applicationId}.fileprovider`. |
| `res/xml/file_paths.xml` | Configures `FileProvider` paths. Exposes `context.filesDir` (`files-path`). Must cover `media/` subdirectory. |
| `ErrorHandler.kt` | Utility classifying exceptions into user-friendly `Snackbar` strings. |
| `ConnectivityObserver.kt` | Checks real-time network connectivity. |
| `RtdbHelper.kt` | Singleton providing the RTDB root `DatabaseReference`. |
| `build.gradle.kts` (app) | Key extra deps: `cloudinary-android:2.5.0`, `material-icons-extended:1.6.3`, `documentfile:1.0.1`, Room 2.6.1. |

---

## 7. Cloudinary Configuration

| Setting | Value |
|---|---|
| Cloud Name | `drpzfssem` |
| API Key | `277344235119189` |
| Upload Preset | `ml_default` (must be set to **Unsigned** in Cloudinary Dashboard) |
| Resource Type | `auto` (accepts any binary blob) |
| Strategy | **Upload-only** (no deletion). Encrypted blobs persist on Cloudinary. Security relies on the decryption key being ephemeral. |

---

## 8. Next Steps for the Incoming Agent

1. **Fix "Corrupt Media" for sender** — In `DecryptedMedia`, add a branch: if `cachedFile.exists()`, skip the download+decrypt entirely and go straight to rendering. Log the exception class name explicitly so the failure mode is clear. Also verify `file_paths.xml` has `path="media"` or `path="."` to allow `FileProvider` to serve `filesDir/media/` files.

2. **Verify build compiles cleanly** — Run `.\gradlew app:compileDebugKotlin` and fix any remaining issues.

3. **Test end-to-end** — Ensure image sent by A appears in B's bubble. Ensure B's bubble shows the image after receiving. Ensure app-closed-then-reopened still shows images (cache hit case).

4. **Handle filename collision** — Use `messageId` as the cache filename prefix (e.g. `<messageId>_<fileName>`) rather than bare `<fileName>` to prevent two users sharing the same filename from corrupting each other's cache.

5. **Update `deleteCachedImage`** — The current `deleteCachedImage()` in `ChatRepository` still looks for `images/shared_image_$messageId.jpg`. Update it to use the new `media/` directory and the `mediaFileName` schema.
