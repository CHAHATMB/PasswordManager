---
title: Vaultly Privacy Policy
permalink: /privacy-policy/
---

# Vaultly Privacy Policy

_Effective date: 31 October 2026_

Vaultly is an offline password manager, authenticator and notes app for Android. This policy explains what data the app handles and where it goes.

## Summary

- Vaultly does **not** collect, transmit, sell or share any personal data.
- The app has **no internet permission**. It cannot send data anywhere.
- There are no accounts, analytics, advertising, crash reporting or third-party tracking SDKs.
- Everything you store stays encrypted on your device.

## Data stored on your device

Vaultly stores the following only on your device, inside an encrypted database (SQLCipher, AES-256):

- Saved logins: website, username, password and note
- Authenticator (TOTP) accounts: issuer, account name and secret key
- Secure notes

The database key is protected by your master password using Argon2id. Your master password is never stored and cannot be recovered by the developer or anyone else. If you forget it, your vault cannot be decrypted.

App settings (theme, auto-lock timeout, list preferences) are stored locally and contain no vault contents.

## Permissions

| Permission | Why it is used |
|---|---|
| Biometric (`USE_BIOMETRIC`) | Optional fingerprint unlock. The unlock key is kept in the Android Keystore. Vaultly never receives or stores your biometric data. |
| Camera (`CAMERA`) | Only to scan QR codes when adding authenticator accounts. Camera frames are processed on the device and are not saved or sent anywhere. |

## Files you choose

- **Encrypted backup export:** Vaultly writes an encrypted backup file (Argon2id + AES-GCM, protected by a password you choose) to a location you pick using the Android file picker. Where that file goes afterwards (for example, a cloud drive) is under your control and subject to that service's privacy policy.
- **Backup and CSV import:** Vaultly reads only the file you select. CSV exports from other password managers are unencrypted; delete them after importing.
- **QR code images:** When you pick an image to scan, it is read locally and not stored.

## Security measures

- Vault data is encrypted at rest and the app locks automatically after a configurable timeout.
- Screenshots and screen recording of the app are blocked, and the app is hidden in the recent-apps view.
- Copied passwords and codes are marked as sensitive and cleared from the clipboard after 30 seconds.
- Android cloud backup of app data is disabled, so your vault is not copied to Google's backup servers.

## Data retention and deletion

Your data stays on your device until you delete it. You can delete individual entries in the app, or remove all data by clearing the app's storage or uninstalling Vaultly. Because the developer never receives your data, there is nothing to delete on any server.

## Children

Vaultly is not directed at children and does not knowingly collect data from anyone.

## Changes to this policy

Updates will be published on this page with a new effective date.

## Contact

Questions about this policy: open an issue at [github.com/CHAHATMB/PasswordManager/issues](https://github.com/CHAHATMB/PasswordManager/issues).
