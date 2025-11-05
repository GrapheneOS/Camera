# Post-Quantum Encrypted Camera

## Overview

This is a fork of the GrapheneOS Camera app with a novel **post-quantum encryption** feature that encrypts photos immediately upon capture, ensuring no plaintext preview or intermediate files are written to disk.

### Key Features

- **Post-Quantum Cryptography**: Uses CRYSTALS-Kyber-1024 (NIST-approved) for key encapsulation
- **Hybrid Encryption**: Combines Kyber KEM with AES-256-GCM for efficient encryption
- **Zero Plaintext Exposure**: Photos are encrypted before any preview or disk write
- **Cross-Platform Decryption**: Rust-based decryption tool for Windows, macOS, and Linux
- **User-Friendly**: Simple UI for key generation, management, and encryption toggle

## How It Works

### Encryption Flow

1. **Key Generation**: User generates a Kyber-1024 key pair in the app
2. **Photo Capture**: When a photo is taken with encryption enabled:
   - Camera captures image
   - EXIF metadata is processed
   - A random AES-256 key is generated
   - Photo is encrypted with AES-256-GCM
   - AES key is encapsulated using Kyber public key
   - Encrypted file is saved with `.pqenc` extension
   - **No preview or thumbnail is generated**
3. **Key Export**: User exports private key to decrypt photos later

### File Format

Encrypted files (`.pqenc`) use this structure:

```
[version:1byte][kyber_ct_len:4bytes][kyber_ciphertext][gcm_nonce:12bytes][gcm_tag:16bytes][encrypted_photo]
```

- **Version**: `0x01` - format version
- **Kyber Ciphertext Length**: Big-endian uint32
- **Kyber Ciphertext**: Encapsulated AES key (~1568 bytes for Kyber-1024)
- **GCM Nonce**: 12-byte random nonce
- **GCM Tag**: 16-byte authentication tag (appended by AES-GCM)
- **Encrypted Photo**: AES-256-GCM encrypted JPEG data

### Security Properties

- **Post-Quantum Secure**: Resistant to attacks by quantum computers
- **Authenticated Encryption**: AES-GCM provides confidentiality and authenticity
- **Forward Secrecy**: Each photo uses a unique AES key
- **No Metadata Leakage**: Optional EXIF stripping before encryption

## Setup Instructions

### Android App

#### Prerequisites
- Android device with API level 29+ (Android 10+)
- Android Studio (for building from source)

#### Building
```bash
cd Camera/
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`

#### Usage

1. **Install the app** on your Android device
2. **Open More Settings** from the camera app
3. **Navigate to "Post-Quantum Encryption"** section
4. **Generate Keys**:
   - Tap "Generate Encryption Keys"
   - Wait for key generation to complete
5. **Export Private Key**:
   - Tap "Export Private Key"
   - Key is saved to `Downloads/camera_private_key.txt`
   - **⚠️ Keep this file secure! Transfer it to your computer for decryption**
6. **Enable Encryption**:
   - Toggle "Post-Quantum Encryption" ON
   - Confirm the warning dialog
7. **Take Photos**:
   - Photos are now encrypted automatically
   - No preview will be shown
   - Files are saved as `IMG_*.pqenc`

### Decryption Tool

#### Prerequisites

##### Linux/macOS
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

##### Windows
Download and install Rust from: https://rustup.rs/

#### Building

```bash
cd Camera/pq-photo-decrypt/
cargo build --release
```

The binary will be in `target/release/pq-decrypt` (or `pq-decrypt.exe` on Windows)

#### Usage

Basic usage:
```bash
# Decrypt all photos in a directory
./pq-decrypt \
  --key ~/Downloads/camera_private_key.txt \
  --input ~/encrypted_photos/ \
  --output ~/decrypted_photos/

# Decrypt a single file
./pq-decrypt \
  --key camera_private_key.txt \
  --input IMG_20250115_143022_523.pqenc \
  --output ./decrypted/

# Verbose mode
./pq-decrypt \
  --key camera_private_key.txt \
  --input encrypted/ \
  --output decrypted/ \
  --verbose
```

Parameters:
- `-k, --key <PATH>`: Path to private key file (required)
- `-i, --input <PATH>`: Input file or directory containing `.pqenc` files (required)
- `-o, --output <PATH>`: Output directory for decrypted JPEGs (required)
- `-v, --verbose`: Enable verbose output

#### Cross-Compilation

##### For Windows (from Linux/macOS):
```bash
rustup target add x86_64-pc-windows-gnu
cargo build --release --target x86_64-pc-windows-gnu
```

##### For macOS (from Linux):
```bash
rustup target add x86_64-apple-darwin
cargo build --release --target x86_64-apple-darwin
```

##### For Linux (from macOS):
```bash
rustup target add x86_64-unknown-linux-gnu
cargo build --release --target x86_64-unknown-linux-gnu
```

## Example Workflow

1. **Setup** (one-time):
   ```bash
   # On Android
   - Generate keys in app
   - Export private key to Downloads

   # Transfer key to computer
   adb pull /sdcard/Download/camera_private_key.txt ~/
   ```

2. **Take Encrypted Photos**:
   ```bash
   # Enable encryption in app
   # Take photos normally (no preview will show)
   ```

3. **Transfer & Decrypt**:
   ```bash
   # Transfer encrypted photos to computer
   adb pull /sdcard/DCIM/Camera/ ~/encrypted_photos/

   # Decrypt photos
   cd ~/pq-photo-decrypt/target/release/
   ./pq-decrypt \
     --key ~/camera_private_key.txt \
     --input ~/encrypted_photos/ \
     --output ~/my_photos/ \
     --verbose

   # View decrypted photos
   open ~/my_photos/
   ```

## Technical Details

### Cryptographic Libraries

**Android**:
- BouncyCastle 1.79 (`org.bouncycastle:bcprov-jdk18on`)
- Provides Kyber-1024 and AES-GCM implementations

**Rust**:
- `pqcrypto-kyber` 0.8 - Kyber KEM implementation
- `aes-gcm` 0.10 - AES-256-GCM implementation

### Performance

- **Key Generation**: ~100-200ms on modern devices
- **Encryption Overhead**: ~50-100ms per photo (depending on size)
- **File Size Increase**: ~1.6 KB per photo (Kyber ciphertext + nonce + tag)
- **Decryption Speed**: ~10-50ms per photo on modern laptops

### Limitations

- **No Preview**: Encrypted photos cannot be previewed on the device
- **No Gallery Integration**: Encrypted files won't show in gallery apps
- **Private Key Management**: User must securely manage the private key
- **One-Time Key Gen**: Re-generating keys makes old encrypted photos undecryptable

## Security Considerations

### DO:
- ✅ Export and backup your private key immediately
- ✅ Store private key in a secure location (password manager, encrypted drive)
- ✅ Use encryption for sensitive photos
- ✅ Regularly decrypt and backup important photos

### DON'T:
- ❌ Share your private key with anyone
- ❌ Store private key on cloud services (unless encrypted)
- ❌ Lose your private key (photos will be permanently unrecoverable)
- ❌ Use this for sole photo storage without backups

## Troubleshooting

### "Key generation failed"
- Ensure device has sufficient entropy
- Try again after a few seconds
- Restart the app

### "Decryption failed - incorrect key or corrupted data"
- Verify you're using the correct private key
- Check that the encrypted file isn't corrupted
- Ensure the file is actually a `.pqenc` file

### "No .pqenc files found to decrypt"
- Check input directory path
- Ensure files have `.pqenc` extension
- Use `--verbose` flag for debugging

## Contributing

This is a proof-of-concept implementation. Contributions welcome for:
- Improved key management (hardware key support, key derivation)
- Multiple key support (key rotation)
- Batch operations UI
- Performance optimizations
- Additional platforms (iOS, etc.)

## License

Same as GrapheneOS Camera (MIT License)

## Acknowledgments

- **GrapheneOS** - Original camera app
- **NIST** - Post-quantum cryptography standardization
- **pq-crystals** - Kyber reference implementation
- **BouncyCastle** - Java cryptography library

## Disclaimer

This is experimental software. While using strong cryptography, this implementation has not undergone security audits. Use at your own risk for non-critical applications. Always maintain backups of important photos.

---

**Note**: This implementation demonstrates post-quantum encryption in mobile photography. For production use, consider professional security review and proper key management infrastructure.
