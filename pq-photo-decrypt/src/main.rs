use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce,
};
use anyhow::{Context, Result};
use clap::Parser;
use pqcrypto_kyber::kyber1024;
use pqcrypto_traits::kem::PublicKey as _;
use pqcrypto_traits::kem::SecretKey as _;
use pqcrypto_traits::kem::Ciphertext as _;
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

const VERSION: u8 = 0x01;
const GCM_NONCE_SIZE: usize = 12;

/// Post-Quantum Photo Decryption Tool
///
/// Decrypts photos encrypted by the GrapheneOS Camera app using post-quantum cryptography
#[derive(Parser, Debug)]
#[command(author, version, about, long_about = None)]
struct Args {
    /// Path to the private key file
    #[arg(short, long)]
    key: PathBuf,

    /// Input directory or file containing encrypted photos (.pqenc)
    #[arg(short, long)]
    input: PathBuf,

    /// Output directory for decrypted photos
    #[arg(short, long)]
    output: PathBuf,

    /// Verbose output
    #[arg(short, long)]
    verbose: bool,
}

fn main() -> Result<()> {
    let args = Args::parse();

    println!("Post-Quantum Photo Decryption Tool");
    println!("===================================\n");

    // Load private key
    if args.verbose {
        println!("Loading private key from: {}", args.key.display());
    }
    let private_key = load_private_key(&args.key)?;

    // Create output directory if it doesn't exist
    if !args.output.exists() {
        fs::create_dir_all(&args.output)
            .context("Failed to create output directory")?;
    }

    // Process input (file or directory)
    let files_to_decrypt = if args.input.is_file() {
        vec![args.input.clone()]
    } else if args.input.is_dir() {
        fs::read_dir(&args.input)
            .context("Failed to read input directory")?
            .filter_map(|entry| entry.ok())
            .map(|entry| entry.path())
            .filter(|path| {
                path.extension()
                    .and_then(|ext| ext.to_str())
                    .map(|ext| ext == "pqenc")
                    .unwrap_or(false)
            })
            .collect()
    } else {
        anyhow::bail!("Input path does not exist");
    };

    if files_to_decrypt.is_empty() {
        println!("No .pqenc files found to decrypt.");
        return Ok(());
    }

    println!("Found {} file(s) to decrypt\n", files_to_decrypt.len());

    let mut success_count = 0;
    let mut error_count = 0;

    for file_path in files_to_decrypt {
        let filename = file_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("unknown");

        if args.verbose {
            print!("Decrypting: {} ... ", filename);
        }

        match decrypt_file(&file_path, &private_key, &args.output, args.verbose) {
            Ok(output_path) => {
                if args.verbose {
                    println!("✓ Saved to: {}", output_path.display());
                } else {
                    println!("✓ {}", filename);
                }
                success_count += 1;
            }
            Err(e) => {
                if args.verbose {
                    println!("✗ Error: {}", e);
                } else {
                    println!("✗ {} - Error: {}", filename, e);
                }
                error_count += 1;
            }
        }
    }

    println!("\n=== Summary ===");
    println!("✓ Successfully decrypted: {}", success_count);
    if error_count > 0 {
        println!("✗ Failed: {}", error_count);
    }

    Ok(())
}

fn load_private_key(key_path: &Path) -> Result<Vec<u8>> {
    let mut file = fs::File::open(key_path)
        .context("Failed to open private key file")?;

    let mut content = String::new();
    file.read_to_string(&mut content)
        .context("Failed to read private key file")?;

    // Skip comment lines (starting with #)
    let key_b64: String = content
        .lines()
        .filter(|line| !line.trim().starts_with('#') && !line.trim().is_empty())
        .collect();

    let key_bytes = base64::decode(key_b64.trim())
        .context("Failed to decode base64 private key")?;

    Ok(key_bytes)
}

fn decrypt_file(
    input_path: &Path,
    private_key_bytes: &[u8],
    output_dir: &Path,
    verbose: bool,
) -> Result<PathBuf> {
    // Read encrypted file
    let encrypted_data = fs::read(input_path)
        .context("Failed to read encrypted file")?;

    // Decrypt the data
    let decrypted_data = decrypt_data(&encrypted_data, private_key_bytes, verbose)?;

    // Determine output filename (replace .pqenc with .jpg)
    let output_filename = input_path
        .file_stem()
        .and_then(|s| s.to_str())
        .ok_or_else(|| anyhow::anyhow!("Invalid filename"))?;

    let output_path = output_dir.join(format!("{}.jpg", output_filename));

    // Write decrypted data
    fs::write(&output_path, decrypted_data)
        .context("Failed to write decrypted file")?;

    Ok(output_path)
}

fn decrypt_data(encrypted_data: &[u8], private_key_bytes: &[u8], verbose: bool) -> Result<Vec<u8>> {
    let mut offset = 0;

    // 1. Parse version
    if encrypted_data.len() < 1 {
        anyhow::bail!("File too small");
    }

    let version = encrypted_data[offset];
    offset += 1;

    if version != VERSION {
        anyhow::bail!("Unsupported encryption version: {}", version);
    }

    // 2. Parse Kyber ciphertext length
    if encrypted_data.len() < offset + 4 {
        anyhow::bail!("Invalid file format: missing ciphertext length");
    }

    let kyber_ct_len = u32::from_be_bytes([
        encrypted_data[offset],
        encrypted_data[offset + 1],
        encrypted_data[offset + 2],
        encrypted_data[offset + 3],
    ]) as usize;
    offset += 4;

    if verbose {
        println!("\n  Kyber ciphertext length: {} bytes", kyber_ct_len);
    }

    // 3. Parse Kyber ciphertext
    if encrypted_data.len() < offset + kyber_ct_len {
        anyhow::bail!("Invalid file format: incomplete Kyber ciphertext");
    }

    let kyber_ciphertext = &encrypted_data[offset..offset + kyber_ct_len];
    offset += kyber_ct_len;

    // 4. Parse GCM nonce
    if encrypted_data.len() < offset + GCM_NONCE_SIZE {
        anyhow::bail!("Invalid file format: missing GCM nonce");
    }

    let nonce = &encrypted_data[offset..offset + GCM_NONCE_SIZE];
    offset += GCM_NONCE_SIZE;

    // 5. Remaining data is AES-GCM ciphertext + tag
    let aes_ciphertext = &encrypted_data[offset..];

    if verbose {
        println!("  GCM nonce: {} bytes", nonce.len());
        println!("  AES ciphertext: {} bytes", aes_ciphertext.len());
    }

    // 6. Decapsulate AES key using Kyber private key
    let private_key = kyber1024::SecretKey::from_bytes(private_key_bytes)
        .map_err(|_| anyhow::anyhow!("Invalid private key format"))?;

    let kyber_ct = kyber1024::Ciphertext::from_bytes(kyber_ciphertext)
        .map_err(|_| anyhow::anyhow!("Invalid Kyber ciphertext"))?;

    let shared_secret = kyber1024::decapsulate(&kyber_ct, &private_key);

    // Use the first 32 bytes of the shared secret as AES-256 key
    let aes_key = &shared_secret.as_bytes()[..32];

    if verbose {
        println!("  AES key length: {} bytes", aes_key.len());
    }

    // 7. Decrypt data with AES-256-GCM
    let cipher = Aes256Gcm::new_from_slice(aes_key)
        .map_err(|_| anyhow::anyhow!("Failed to initialize AES cipher"))?;

    let nonce_arr = Nonce::from_slice(nonce);

    let plaintext = cipher
        .decrypt(nonce_arr, aes_ciphertext)
        .map_err(|_| anyhow::anyhow!("Decryption failed - incorrect key or corrupted data"))?;

    if verbose {
        println!("  Decrypted data: {} bytes", plaintext.len());
    }

    Ok(plaintext)
}
